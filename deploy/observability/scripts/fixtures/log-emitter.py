"""Write synthetic logs through PID 1; /partial holds back the newline."""
import sys

from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        line = self.rfile.read(int(self.headers['Content-Length'])).decode()
        stream = sys.stderr if self.path.startswith('/stderr') else sys.stdout
        print(line, end='' if self.path.endswith('/partial') else '\n', file=stream, flush=True)
        self.send_response(204)
        self.end_headers()

    def do_GET(self):
        self.send_response(200)
        self.end_headers()

    def log_message(self, *_):
        pass


HTTPServer(('0.0.0.0', 8000), Handler).serve_forever()
