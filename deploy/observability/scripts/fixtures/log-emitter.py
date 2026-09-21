"""Local smoke fixture: write submitted synthetic log lines through PID 1 stdout."""
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        line = self.rfile.read(int(self.headers['Content-Length'])).decode()
        print(line, flush=True)
        self.send_response(204)
        self.end_headers()

    def do_GET(self):
        self.send_response(200)
        self.end_headers()

    def log_message(self, *_):
        pass


HTTPServer(('0.0.0.0', 8000), Handler).serve_forever()
