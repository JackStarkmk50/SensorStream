import socket
import sys
import cv2
import numpy as np
import threading
import asyncio
import time

try:
    import websockets
except ImportError:
    print("[!] 'websockets' library not found. Run: pip install websockets")
    websockets = None

try:
    from pyngrok import ngrok
except ImportError:
    ngrok = None

# --- CONFIGURATION ---
NGROK_AUTH_TOKEN = "36EnpN0f0tx9hre6EMpt1jRpHeb_38GYquy1KabEJGskmKeHL" 
HOST = '0.0.0.0'
PORT = 5555 

# --- SENSOR FUSION STATE ---
class OrientationState:
    def __init__(self):
        self.roll = 0.0
        self.pitch = 0.0
        self.yaw = 0.0
        self.last_time = time.time()
        self.lock = threading.Lock()

state = OrientationState()

def draw_cube(img, roll, pitch, yaw):
    """Draws a 3D cube with CORRECT Android axes mapping."""
    h, w = img.shape[:2]
    cx, cy = w // 2, h // 2
    size = 100
    
    # 3D vertices
    points = np.array([
        [-1, -1, -1], [1, -1, -1], [1, 1, -1], [-1, 1, -1],
        [-1, -1, 1], [1, -1, 1], [1, 1, 1], [-1, 1, 1]
    ], dtype=np.float32) * size

    # --- AXIS MAPPING (Standard Android) ---
    # Pitch: Rotation around X
    rx = np.array([[1, 0, 0], [0, np.cos(pitch), -np.sin(pitch)], [0, np.sin(pitch), np.cos(pitch)]])
    # Roll: Rotation around Y
    ry = np.array([[np.cos(roll), 0, np.sin(roll)], [0, 1, 0], [-np.sin(roll), 0, np.cos(roll)]])
    # Yaw: Rotation around Z
    rz = np.array([[np.cos(yaw), -np.sin(yaw), 0], [np.sin(yaw), np.cos(yaw), 0], [0, 0, 1]])
    
    # Rotation Order: Yaw -> Pitch -> Roll
    rot = rz @ rx @ ry
    projected = (points @ rot.T)
    
    z_dist = 400
    pts2d = []
    for p in projected:
        f = z_dist / (z_dist + p[2])
        pts2d.append([int(cx + p[0] * f), int(cy + p[1] * f)])
    
    edges = [(0,1), (1,2), (2,3), (3,0), (4,5), (5,6), (6,7), (7,4), (0,4), (1,5), (2,6), (3,7)]
    for e in edges:
        cv2.line(img, tuple(pts2d[e[0]]), tuple(pts2d[e[1]]), (0, 255, 0), 2)
    
    # UI Overlay
    cv2.putText(img, "VERIFIED SENSOR FUSION", (20, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 255), 1)
    
    # Show 0-360 values
    r_deg = (np.degrees(roll) + 360) % 360
    p_deg = (np.degrees(pitch) + 360) % 360
    y_deg = (np.degrees(yaw) + 360) % 360

    cv2.putText(img, f"ROLL:  {r_deg:.1f}", (20, 60), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)
    cv2.putText(img, f"PITCH: {p_deg:.1f}", (20, 90), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)
    cv2.putText(img, f"YAW:   {y_deg:.1f}", (20, 120), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)

def imu_visualizer_thread():
    viz_img = np.zeros((500, 500, 3), dtype=np.uint8)
    while True:
        viz_img.fill(25)
        with state.lock:
            r, p, y = state.roll, state.pitch, state.yaw
        draw_cube(viz_img, r, p, y)
        cv2.imshow("3D Fusion Simulator", viz_img)
        if cv2.waitKey(20) & 0xFF == ord('q'): break
    cv2.destroyWindow("3D Fusion Simulator")

# --- SENSOR FUSION LOGIC ---

def parse_imu_and_fuse(text):
    try:
        parts = [p.strip() for p in text.split(',')]
        now = time.time()
        
        ax, ay, az = None, None, None
        gx, gy, gz = None, None, None
        mx, my, mz = None, None, None

        for i in range(len(parts)):
            if parts[i] == '3': # Accel
                ax, ay, az = float(parts[i+1]), float(parts[i+2]), float(parts[i+3])
            elif parts[i] == '4': # Gyro
                gx, gy, gz = float(parts[i+1]), float(parts[i+2]), float(parts[i+3])
            elif parts[i] == '5': # Mag
                mx, my, mz = float(parts[i+1]), float(parts[i+2]), float(parts[i+3])

        with state.lock:
            dt = now - state.last_time
            state.last_time = now
            
            # 1. Integrate Gyro (Instant response)
            if gx is not None:
                # Android Mapping: X=Pitch, Y=Roll, Z=Yaw
                state.pitch += gx * dt
                state.roll += gy * dt
                state.yaw += gz * dt

            # 2. Complementary Filter Correction (Stability)
            alpha = 0.98
            
            if ax is not None:
                # Pitch from Gravity (Tilt forward/back)
                a_pitch = np.arctan2(ay, np.sqrt(ax**2 + az**2))
                # Roll from Gravity (Tilt left/right)
                a_roll = np.arctan2(-ax, az)
                
                state.roll = alpha * state.roll + (1 - alpha) * a_roll
                state.pitch = alpha * state.pitch + (1 - alpha) * a_pitch

            if mx is not None and ax is not None:
                # Tilt-Compensated Yaw (Compass heading)
                Yh = my * np.cos(state.roll) - mz * np.sin(state.roll)
                Xh = mx * np.cos(state.pitch) + my * np.sin(state.pitch) * np.sin(state.roll) + mz * np.sin(state.pitch) * np.cos(state.roll)
                m_yaw = np.arctan2(-Yh, Xh)
                
                # Correct Yaw Wrap
                diff = m_yaw - state.yaw
                while diff > np.pi: diff -= 2*np.pi
                while diff < -np.pi: diff += 2*np.pi
                state.yaw = state.yaw + (1 - alpha) * diff
    except: pass

# --- NETWORK HANDLERS ---

async def combined_ws_handler(websocket):
    addr = websocket.remote_address
    print(f"[*] Connected: {addr}")
    try:
        async for message in websocket:
            if isinstance(message, bytes):
                # Handle Sync Header if present (12 bytes: MAGIC + TIMESTAMP)
                payload = message
                if len(message) > 12 and message[:4] == b'SYNC':
                    payload = message[12:] # Skip header
                
                nparr = np.frombuffer(payload, np.uint8)
                frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
                if frame is not None:
                    # Rotation for landscape stream
                    frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
                    cv2.imshow("Stream", frame)
                    cv2.waitKey(1)
                else:
                    try:
                        text = message.decode('utf-8').strip()
                        parse_imu_and_fuse(text)
                        # print(f"[DATA] {text}") # Uncomment for debugging
                    except: pass
            else:
                parse_imu_and_fuse(message.strip())
    except Exception as e: print(f"[*] Closed: {e}")

async def process_request(connection, request):
    if "connection" in request.headers:
        if "upgrade" in request.headers["connection"].lower():
            request.headers["connection"] = "Upgrade"
    return None

def start_ws_mode():
    threading.Thread(target=imu_visualizer_thread, daemon=True).start()
    async def run():
        async with websockets.serve(combined_ws_handler, HOST, PORT, process_request=process_request):
            await asyncio.Future()
    try: asyncio.run(run())
    except KeyboardInterrupt: print("\n[*] Stopped.")

if __name__ == "__main__":
    if ngrok and NGROK_AUTH_TOKEN:
        try:
            ngrok.set_auth_token(NGROK_AUTH_TOKEN)
            tunnel = ngrok.connect(PORT, "http")
            print(f"\n🚀 NGROK URL: {tunnel.public_url.replace('http', 'ws')}\n")
        except: pass
    start_ws_mode()
