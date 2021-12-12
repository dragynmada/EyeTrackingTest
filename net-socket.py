#!/usr/bin/env python3

import socket
import pyautogui

HOST = '192.168.56.1'  # Standard loopback interface address (localhost)
PORT = 7800        # Port to listen on (non-privileged ports are > 1023)

pyautogui.moveTo(100, 100)

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.bind((HOST, PORT))
s.listen(10)
print("Listening for connection...")
conn, addr = s.accept()
with conn:
    print('Connected by', addr)
    while True:
        data = conn.recv(1024)
        if not data:
            break
        print(data)