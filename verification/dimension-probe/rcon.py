import argparse
import socket
import struct


def read_exact(connection, size):
    data = bytearray()
    while len(data) < size:
        chunk = connection.recv(size - len(data))
        if not chunk:
            raise ConnectionError("RCON connection closed")
        data.extend(chunk)
    return bytes(data)


def read_packet(connection):
    length = struct.unpack("<i", read_exact(connection, 4))[0]
    payload = read_exact(connection, length)
    request_id, packet_type = struct.unpack("<ii", payload[:8])
    body = payload[8:-2].decode("utf-8", errors="replace")
    return request_id, packet_type, body


def write_packet(connection, request_id, packet_type, body):
    encoded = body.encode("utf-8") + b"\x00"
    payload = struct.pack("<ii", request_id, packet_type) + encoded + b"\x00"
    connection.sendall(struct.pack("<i", len(payload)) + payload)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25567)
    parser.add_argument("--password", default="probe-only")
    args = parser.parse_args()

    with socket.create_connection((args.host, args.port), timeout=60) as connection:
        write_packet(connection, 1, 3, args.password)
        request_id, packet_type, body = read_packet(connection)
        if request_id == -1 or packet_type != 2:
            raise RuntimeError(f"RCON authentication failed: {body}")
        write_packet(connection, 2, 2, args.command)
        while True:
            request_id, packet_type, body = read_packet(connection)
            if request_id == 2:
                print(body)
                return


if __name__ == "__main__":
    main()
