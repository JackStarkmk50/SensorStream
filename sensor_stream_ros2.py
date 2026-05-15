#!/usr/bin/env python3
import rclpy
from rclpy.node import Node
from sensor_msgs.msg import Image, Imu
from cv_bridge import CvBridge
import cv2
import numpy as np
import asyncio
import websockets
import threading
import struct
import time
import socket

# Try to import pyngrok for automatic tunneling
try:
    from pyngrok import ngrok
except ImportError:
    ngrok = None

class SensorStreamRosNode(Node):
    def __init__(self):
        super().__init__('sensor_stream_bridge')
        
        # Parameters
        self.declare_parameter('mode', 'ws') # 'ws' or 'udp'
        self.declare_parameter('port', 5555)
        self.declare_parameter('ngrok_token', '36EnpN0f0tx9hre6EMpt1jRpHeb_38GYquy1KabEJGskmKeHL')
        
        self.mode = self.get_parameter('mode').get_parameter_value().string_value
        self.port = self.get_parameter('port').get_parameter_value().integer_value
        self.ngrok_token = self.get_parameter('ngrok_token').get_parameter_value().string_value
        
        # Publishers
        self.image_pub = self.create_publisher(Image, '/camera/image_raw', 10)
        self.imu_pub = self.create_publisher(Imu, '/imu/data_raw', 50)
        
        self.bridge = CvBridge()
        self.sync_magic = b'SYNC'
        self.clock_offset_ns = None
        
        # Diagnostics
        self.frame_count = 0
        self.imu_count = 0
        self.last_diag_time = time.time()
        
        self.get_logger().info(f"🚀 Starting in {self.mode.upper()} mode on port {self.port}")
        
        if self.mode == 'ws':
            self.thread = threading.Thread(target=self.run_ws_server, daemon=True)
        else:
            self.thread = threading.Thread(target=self.run_udp_server, daemon=True)
            
        self.thread.start()

    def handle_fragment(self, data):
        if not hasattr(self, 'fragments'):
            self.fragments = {}
        
        try:
            # Header: FRAG (4) + ID (4) + INDEX (2) + TOTAL (2) = 12 bytes
            import struct
            frame_id = struct.unpack(">I", data[4:8])[0]
            index = struct.unpack(">H", data[8:10])[0]
            total = struct.unpack(">H", data[10:12])[0]
            payload = data[12:]
            
            if frame_id not in self.fragments:
                self.fragments[frame_id] = {"chunks": {}, "total": total, "time": time.time()}
            
            self.fragments[frame_id]["chunks"][index] = payload
            
            if len(self.fragments[frame_id]["chunks"]) == total:
                full_data = b"".join(self.fragments[frame_id]["chunks"][i] for i in range(total))
                self.handle_binary(full_data)
                del self.fragments[frame_id]
                
            # Cleanup
            now = time.time()
            for fid in list(self.fragments.keys()):
                if now - self.fragments[fid]["time"] > 1.0:
                    del self.fragments[fid]
        except: pass

    def run_udp_server(self):
        import select
        imu_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        imu_sock.bind(("0.0.0.0", self.port))
        cam_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        cam_sock.bind(("0.0.0.0", self.port + 1))
        
        self.get_logger().info(f"📡 UDP Listening on ports {self.port} (IMU) and {self.port+1} (Camera)")
        
        while rclpy.ok():
            readable, _, _ = select.select([imu_sock, cam_sock], [], [], 0.1)
            for s in readable:
                data, addr = s.recvfrom(65535)
                if data.startswith(b'FRAG'):
                    self.handle_fragment(data)
                else:
                    self.handle_binary(data)

    def run_ws_server(self):
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        
        async def start_server():
            if ngrok and self.ngrok_token:
                try:
                    ngrok.set_auth_token(self.ngrok_token)
                    tunnel = ngrok.connect(self.port, "http")
                    public_url = tunnel.public_url.replace("http", "ws")
                    self.get_logger().info(f"🔗 NGROK URL: {public_url}")
                except Exception as e:
                    self.get_logger().warn(f"Ngrok failed: {e}")

            async with websockets.serve(self.ws_handler, "0.0.0.0", self.port):
                await asyncio.Future()

        loop.run_until_complete(start_server())

    async def ws_handler(self, websocket):
        try:
            async for message in websocket:
                if isinstance(message, bytes):
                    self.handle_binary(message)
                else:
                    self.handle_text(message)
        except: pass

    def get_ros_time(self, android_ns):
        now_ns = time.time_ns()
        if self.clock_offset_ns is None:
            self.clock_offset_ns = now_ns - android_ns
        return rclpy.time.Time(nanoseconds=android_ns + self.clock_offset_ns).to_msg()

    def handle_binary(self, data):
        if len(data) > 12 and data[:4] == self.sync_magic:
            ts_ns = struct.unpack('>Q', data[4:12])[0]
            nparr = np.frombuffer(data[12:], np.uint8)
            cv_image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            if cv_image is not None:
                # Fast rotation
                cv_image = cv2.transpose(cv_image)
                cv_image = cv2.flip(cv_image, 1)
                
                msg = self.bridge.cv2_to_imgmsg(cv_image, encoding="bgr8")
                msg.header.stamp = self.get_ros_time(ts_ns)
                msg.header.frame_id = "camera_link"
                self.image_pub.publish(msg)
                self.frame_count += 1
        else:
            try:
                text = data.decode('utf-8').strip()
                self.handle_text(text)
            except: pass
        self.log_diagnostics()

    def handle_text(self, text):
        try:
            parts = [p.strip() for p in text.split(',')]
            if len(parts) < 5: return
            ts_ns = int(float(parts[0]) * 1e9)
            imu_msg = Imu()
            imu_msg.header.stamp = self.get_ros_time(ts_ns)
            imu_msg.header.frame_id = "imu_link"
            for i in range(1, len(parts)-3, 4):
                id = parts[i]
                vals = [float(parts[i+1]), float(parts[i+2]), float(parts[i+3])]
                if id == '3': imu_msg.linear_acceleration.x, imu_msg.linear_acceleration.y, imu_msg.linear_acceleration.z = vals
                elif id == '4': imu_msg.angular_velocity.x, imu_msg.angular_velocity.y, imu_msg.angular_velocity.z = vals
            self.imu_pub.publish(imu_msg)
            self.imu_count += 1
        except: pass

    def log_diagnostics(self):
        now = time.time()
        if now - self.last_diag_time > 2.0:
            dt = now - self.last_diag_time
            self.get_logger().info(f"📊 [{self.mode.upper()}] Rates: Camera: {self.frame_count/dt:.1f} Hz | IMU: {self.imu_count/dt:.1f} Hz")
            self.frame_count = 0
            self.imu_count = 0
            self.last_diag_time = now

def main(args=None):
    rclpy.init(args=args)
    node = SensorStreamRosNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt: pass
    finally:
        node.destroy_node()
        rclpy.shutdown()

if __name__ == '__main__':
    main()
