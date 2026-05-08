import socket
import sys

def main():
    # Replace with the IP address of your PC (or 0.0.0.0 to listen on all interfaces)
    HOST = '0.0.0.0'
    PORT = 5555

    try:
        # Create a UDP socket
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        
        # Bind the socket to the port
        sock.bind((HOST, PORT))
        print(f"[*] Listening for UDP sensor stream on {HOST}:{PORT}")
        print("[*] Make sure to enter your PC's IP address and port 5555 in the SensorStream App settings.")
        
    except socket.error as e:
        print(f"Failed to create or bind socket. Error code: {e}")
        sys.exit()

    try:
        # Set a small timeout so the blocking recvfrom doesn't ignore Ctrl+C on Windows
        sock.settimeout(1.0)
        
        while True:
            try:
                # Receive data from client (buffer size is 1024 bytes)
                data, addr = sock.recvfrom(1024)
            except socket.timeout:
                # Timeout happened, loop back up to check for KeyboardInterrupt
                continue
                
            if not data:
                break
                
            # Decode the byte array to string and strip trailing whitespace/newlines
            text = data.decode('utf-8').strip()
            
            # Print the received CSV string
            print(text)
            
            # Optional: You can parse the CSV data here
            # parts = text.split(',')
            # timestamp = parts[0]
            # ... process further ...
            
    except KeyboardInterrupt:
        print("\n[*] Stopped receiving data.")
    finally:
        sock.close()

if __name__ == "__main__":
    main()
