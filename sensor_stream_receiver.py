import asyncio
import websockets
import cv2
import numpy as np
import threading
import time
import socket
import select
import struct
import argparse
from dataclasses import dataclass

# Try to import pyngrok for automatic tunneling
try:
    from pyngrok import ngrok
except ImportError:
    ngrok = None

# Configuration
HOST = "0.0.0.0"
PORT = 5555
NGROK_AUTH_TOKEN = "36EnpN0f0tx9hre6EMpt1jRpHeb_38GYquy1KabEJGskmKeHL"

@dataclass
class DroneState:
    pitch: float = 0.0
    roll: float = 0.0
    yaw: float = 0.0
    # Offsets for calibration
    offset_pitch: float = 0.0
    offset_roll: float = 0.0
    offset_yaw: float = 0.0
    last_time: float = time.time()
    lock: threading.Lock = threading.Lock()

state = DroneState()

def calibrate_orientation():
    global state
    with state.lock:
        state.offset_pitch = state.pitch
        state.offset_roll = state.roll
        state.offset_yaw = state.yaw
        print("🎯 Calibration Successful! Current position is now HOME.")

def parse_imu_and_fuse(text):
    global state
    try:
        parts = [p.strip() for p in text.split(',')]
        if len(parts) < 2: return
        now = time.time()
        ax, ay, az = None, None, None
        gx, gy, gz = None, None, None
        mx, my, mz = None, None, None

        for i in range(1, len(parts)-3, 4):
            id = parts[i]
            if id == '3': ax, ay, az = float(parts[i+1]), float(parts[i+2]), float(parts[i+3])
            elif id == '4': gx, gy, gz = float(parts[i+1]), float(parts[i+2]), float(parts[i+3])
            elif id == '5': mx, my, mz = float(parts[i+1]), float(parts[i+2]), float(parts[i+3])

        with state.lock:
            dt = now - state.last_time
            state.last_time = now
            if gx is not None:
                state.pitch += gx * dt
                state.roll += gy * dt
                state.yaw += gz * dt
            alpha = 0.98
            if ax is not None:
                a_pitch = np.arctan2(ay, np.sqrt(ax**2 + az**2))
                a_roll = np.arctan2(-ax, az)
                state.roll = alpha * state.roll + (1 - alpha) * a_roll
                state.pitch = alpha * state.pitch + (1 - alpha) * a_pitch
            if mx is not None and ax is not None:
                Yh = my * np.cos(state.roll) - mz * np.sin(state.roll)
                Xh = mx * np.cos(state.pitch) + my * np.sin(state.pitch) * np.sin(state.roll) + mz * np.sin(state.pitch) * np.cos(state.roll)
                m_yaw = np.arctan2(-Yh, Xh)
                state.yaw = alpha * state.yaw + (1 - alpha) * m_yaw
    except: pass

def handle_binary_frame(data):
    payload = data
    if len(data) > 12 and data[:4] == b'SYNC':
        payload = data[12:]
    nparr = np.frombuffer(payload, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if frame is not None:
        frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
        cv2.imshow("Stream", frame)
        cv2.waitKey(1)
    else:
        try:
            text = data.decode('utf-8').strip()
            parse_imu_and_fuse(text)
        except: pass

async def combined_ws_handler(websocket):
    print(f"[*] Connected: {websocket.remote_address}")
    try:
        async for message in websocket:
            if isinstance(message, bytes):
                handle_binary_frame(message)
            else:
                parse_imu_and_fuse(message.strip())
    except: pass

async def process_request(connection, request):
    if "connection" in request.headers:
        if "upgrade" in request.headers["connection"].lower():
            request.headers["connection"] = "Upgrade"
    return None

def start_ws_background():
    async def run():
        async with websockets.serve(combined_ws_handler, HOST, PORT, process_request=process_request):
            await asyncio.Future()
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    loop.run_until_complete(run())

fragments = {} # {frame_id: {chunks: {}, total: N, time: T}}

def handle_fragment(data):
    try:
        # Header: FRAG (4) + ID (4) + INDEX (2) + TOTAL (2) = 12 bytes
        frame_id = struct.unpack(">I", data[4:8])[0]
        index = struct.unpack(">H", data[8:10])[0]
        total = struct.unpack(">H", data[10:12])[0]
        payload = data[12:]
        
        if frame_id not in fragments:
            fragments[frame_id] = {"chunks": {}, "total": total, "time": time.time()}
        
        fragments[frame_id]["chunks"][index] = payload
        
        # Check if complete
        if len(fragments[frame_id]["chunks"]) == total:
            # Reassemble
            full_data = b"".join(fragments[frame_id]["chunks"][i] for i in range(total))
            handle_binary_frame(full_data)
            del fragments[frame_id]
            
        # Cleanup old fragments (> 1 sec)
        now = time.time()
        for fid in list(fragments.keys()):
            if now - fragments[fid]["time"] > 1.0:
                del fragments[fid]
    except: pass

def start_udp_background():
    imu_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    imu_sock.bind((HOST, PORT))
    cam_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    cam_sock.bind((HOST, PORT + 1))
    print(f"📡 UDP Mode: Listening on ports {PORT} and {PORT+1}")
    while True:
        readable, _, _ = select.select([imu_sock, cam_sock], [], [], 0.1)
        for s in readable:
            data, addr = s.recvfrom(65535)
            if data.startswith(b'FRAG'):
                handle_fragment(data)
            else:
                handle_binary_frame(data)

def run_visualizer():
    from vpython import canvas, box, vector, rate, color, label, keysdown
    scene = canvas(title='3D Drone Orientation', width=800, height=600, center=vector(0,0,0), background=color.black)
    drone_body = box(pos=vector(0,0,0), size=vector(4, 0.2, 2), color=color.cyan)
    front_marker = box(pos=vector(1.8, 0.2, 0), size=vector(0.4, 0.3, 0.4), color=color.red)
    info_label = label(pos=vector(0, 3, 0), text='Wait for data...', box=False)
    
    print("\n⌨️  Press 'C' in the browser window to CALIBRATE the orientation!\n")

    while True:
        rate(60)
        
        # Handle Calibration Key
        k = keysdown()
        if 'c' in k:
            calibrate_orientation()
            time.sleep(0.2) # Debounce
            
        with state.lock:
            # Apply Calibration Offsets
            p = state.pitch - state.offset_pitch
            r = state.roll - state.offset_roll
            y = state.yaw - state.offset_yaw
            
        # Convert to Degrees for the Label
        p_deg = np.degrees(p)
        r_deg = np.degrees(r)
        y_deg = (np.degrees(y) + 360) % 360 # 0 to 360 Degree Yaw
            
        drone_body.axis = vector(np.cos(y), 0, -np.sin(y))
        drone_body.up = vector(np.sin(r), np.cos(r), 0)
        front_marker.pos = drone_body.pos + drone_body.axis * 1.8 + vector(0, 0.2, 0)
        
        info_label.text = f"Pitch: {p_deg:.1f}\nRoll: {r_deg:.1f}\nYaw: {y_deg:.1f}"

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", type=str, default="ws", help="Stream mode: ws or udp")
    args = parser.parse_args()

    if args.mode == "ws":
        if ngrok and NGROK_AUTH_TOKEN:
            try:
                ngrok.set_auth_token(NGROK_AUTH_TOKEN)
                tunnel = ngrok.connect(PORT, "http")
                print(f"\n🚀 NGROK URL: {tunnel.public_url.replace('http', 'ws')}\n")
            except: pass
        threading.Thread(target=start_ws_background, daemon=True).start()
    else:
        threading.Thread(target=start_udp_background, daemon=True).start()
    
    # RUN VISUALIZER IN MAIN THREAD
    run_visualizer()
