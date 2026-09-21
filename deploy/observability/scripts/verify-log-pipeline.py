#!/usr/bin/env python3
"""Local Docker E2E: real json-file -> Alloy -> Loki -> Grafana/Tempo.

Uses repository configs and isolated named volumes, network and random loopback
ports. Never calls AWS or deploys the backend. Only its own project is removed.
"""
import base64
import copy
import datetime
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[3]
OBS = ROOT / 'deploy/observability'
PROJECT = 'beach-logs-test-' + secrets.token_hex(4)
REPORT = ROOT / 'build/reports/observability/log-pipeline.json'
ENV = dict(os.environ, LOKI_URL='http://loki:3100/loki/api/v1/push',
           APP_ENVIRONMENT='dev', LOG_HOST=PROJECT, LOKI_RETENTION_PERIOD='72h')
PASSWORD = secrets.token_hex(20)
checks = []


def run(*args, **kwargs):
    return subprocess.check_output(args, text=True, env=ENV, **kwargs).strip()


def request(url, payload=None, headers=None):
    req = urllib.request.Request(url, data=payload, headers=headers or {})
    with urllib.request.urlopen(req, timeout=8) as response:
        return response.read().decode()


def wait_for(description, check, timeout=90):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            value = check()
            if value:
                return value
        except (urllib.error.URLError, TimeoutError, ConnectionError, json.JSONDecodeError) as exc:
            last = str(exc)
        time.sleep(1)
    raise AssertionError(f'Timed out: {description}; last error: {last}')


def passed(message):
    checks.append(message)
    print('PASS ' + message, flush=True)


def main():
    # Refuse to mix another backend's logs into the test Loki instance.
    if run('docker', 'ps', '-aq', '--filter', 'label=beach.logs=backend'):
        raise RuntimeError('A labelled backend exists; use an isolated Docker daemon for this test.')
    docker_logs = run('docker', 'info', '--format', '{{.DockerRootDir}}') + '/containers'
    base = json.loads(run('docker', 'compose', '-f', str(OBS / 'compose/docker-compose.yml'),
                          'config', '--format', 'json'))
    agent = json.loads(run('docker', 'compose', '-f', str(OBS / 'app-agent/docker-compose.yml'),
                           'config', '--format', 'json'))
    services = {n: copy.deepcopy(base['services'][n]) for n in ['loki', 'tempo', 'grafana']}
    services['alloy'] = agent['services']['alloy']
    volumes = {}
    # Keep actual command, image, read-only config mounts and env configuration.
    # Replace only production storage, host ports and global container names.
    for name, service in services.items():
        service.pop('container_name', None)
        service.pop('depends_on', None)
        service['restart'] = 'no'
        for port in service.get('ports', []):
            port.update(host_ip='127.0.0.1', published='0')
        for mount in service.get('volumes', []):
            if mount['target'] == '/var/log/docker':
                mount['source'] = docker_logs
            elif not mount.get('read_only', False):
                volume = name + '-data'
                volumes[volume] = {}
                mount.clear()
                mount.update(type='volume', source=volume,
                             target={'loki': '/loki', 'tempo': '/var/tempo',
                                     'grafana': '/var/lib/grafana', 'alloy': '/var/lib/alloy'}[name])
                if name == 'alloy':
                    mount['volume'] = {'nocopy': True}
        if name == 'alloy':
            service['environment']['LOG_HOST'] = PROJECT
    services['grafana']['environment'].update(GF_SECURITY_ADMIN_PASSWORD=PASSWORD,
                                               GF_ANALYTICS_REPORTING_ENABLED='false')
    logging_contract = json.loads(run('docker', 'compose', '-f',
        str(OBS / 'app-agent/backend-logging.override.yml'), 'config', '--no-consistency', '--format', 'json'))['services']['app']
    for name, label in [('emitter', 'backend'), ('ignored', 'unrelated')]:
        services[name] = {
            'image': 'python:3.12-slim', 'command': ['python3', '-u', '/fixture.py'],
            'volumes': [{'type': 'bind', 'source': str(OBS / 'scripts/fixtures/log-emitter.py'),
                         'target': '/fixture.py', 'read_only': True}],
            'ports': [{'target': 8000, 'published': '0', 'host_ip': '127.0.0.1'}],
            'labels': {'beach.logs': label}, 'logging': logging_contract['logging'],
        }
    document = {'services': services, 'volumes': volumes}
    with tempfile.TemporaryDirectory(prefix=PROJECT) as temp:
        config = Path(temp) / 'compose.json'
        config.write_text(json.dumps(document))
        os.chmod(config, 0o600)

        def compose(*args):
            return run('docker', 'compose', '-p', PROJECT, '-f', str(config), *args)

        def address(service, port):
            return 'http://' + compose('port', service, str(port))

        try:
            compose('up', '-d')
            loki, tempo, grafana = address('loki', 3100), address('tempo', 3200), address('grafana', 3000)
            emitter, ignored = address('emitter', 8000), address('ignored', 8000)
            wait_for('Loki readiness', lambda: 'ready' in request(loki + '/ready'))
            wait_for('Tempo readiness', lambda: 'ready' in request(tempo + '/ready'))
            wait_for('Grafana readiness', lambda: json.loads(request(grafana + '/api/health'))['database'] == 'ok')
            wait_for('log emitters', lambda: request(emitter) == '' and request(ignored) == '')
            passed('Local Loki, Tempo, Grafana and Docker log emitters ready')

            def query(expression):
                qs = urllib.parse.urlencode({'query': expression, 'limit': '1000'})
                return json.loads(request(loki + '/loki/api/v1/query_range?' + qs))['data']['result']

            selector = '{app="beach-complex",env="dev",service="backend",host="' + PROJECT + '"}'

            def entries(marker):
                return [v for stream in query(selector + ' |= "' + marker + '"') for v in stream['values']]

            trace_id, span_id = secrets.token_hex(16), secrets.token_hex(8)
            record = {'message': 'pr4-first-' + PROJECT, 'traceId': trace_id,
                      'spanId': span_id, 'requestId': 'synthetic-request', 'userId': 'synthetic-user',
                      'notificationId': 'synthetic-notification', 'outboxEventId': 'synthetic-outbox'}
            line = json.dumps(record, separators=(',', ':'))
            request(emitter, line.encode())
            found = wait_for('Docker -> Alloy -> Loki', lambda: entries(record['message']))
            assert found[0][1].rstrip('\n') == line, 'Original app JSON was altered'
            parsed = query(selector + ' | json | traceId="' + trace_id + '"')
            assert parsed, 'traceId JSON filter did not work'
            # Query results also contain structured metadata (e.g. detected_level).
            # The series endpoint reports the actual indexed stream labels.
            series_qs = urllib.parse.urlencode({'match[]': selector})
            series = json.loads(request(loki + '/loki/api/v1/series?' + series_qs))['data']
            assert series, 'No indexed series found'
            for labels in series:
                assert set(labels) == {'app', 'env', 'service', 'host'}, labels
            request(ignored, json.dumps({'message': 'must-not-collect-' + PROJECT}).encode())
            # Send a backend barrier after the negative fixture, then let batching finish.
            request(emitter, json.dumps({'message': 'barrier-' + PROJECT}).encode())
            wait_for('backend barrier', lambda: entries('barrier-' + PROJECT))
            time.sleep(6)
            assert not entries('must-not-collect-' + PROJECT)
            passed('Real Docker JSON is preserved; unrelated logs excluded; only four low-cardinality labels')

            now = time.time_ns()
            span = {'resourceSpans': [{'resource': {'attributes': [
                {'key': 'service.name', 'value': {'stringValue': 'beach-complex'}}]},
                'scopeSpans': [{'scope': {'name': 'pr4-local-smoke'}, 'spans': [{
                    'traceId': trace_id, 'spanId': span_id, 'name': 'synthetic-log-correlation',
                    'kind': 2, 'startTimeUnixNano': str(now - 1000000), 'endTimeUnixNano': str(now),
                }]}]}]}
            request(address('tempo', 4318) + '/v1/traces', json.dumps(span).encode(),
                    {'Content-Type': 'application/json'})
            auth = {'Authorization': 'Basic ' + base64.b64encode(('admin:' + PASSWORD).encode()).decode()}

            def grafana_get(path):
                return json.loads(request(grafana + path, headers=auth))

            ds = grafana_get('/api/datasources/uid/loki')
            field = ds['jsonData']['derivedFields'][0]
            assert field['datasourceUid'] == 'tempo'
            assert field['url'] == '${__value.raw}', field
            assert re.search(field['matcherRegex'], line).group(1) == trace_id
            assert re.search(field['matcherRegex'], '[traceId=' + trace_id + ',spanId=x]').group(1) == trace_id
            tempo_ds = grafana_get('/api/datasources/uid/tempo')
            reverse = tempo_ds['jsonData']['tracesToLogsV2']
            assert reverse['datasourceUid'] == 'loki'
            assert '${__span.traceId}' in reverse['query']
            wait_for('trace through Grafana proxy', lambda: grafana_get('/api/datasources/proxy/uid/tempo/api/traces/' + trace_id))
            health = wait_for('Grafana Loki datasource', lambda: grafana_get('/api/datasources/uid/loki/health'))
            assert health['status'] == 'OK', health
            reverse_query = reverse['query'].replace('${__span.traceId}', trace_id)
            proxy_query = urllib.parse.urlencode({'query': reverse_query})
            assert grafana_get('/api/datasources/proxy/uid/loki/loki/api/v1/query_range?' + proxy_query)['data']['result']
            passed('Grafana datasource health and provisioned log-to-trace / trace-to-log targets resolve')

            # Graceful stop persists offsets; logs produced while stopped must be read on restart.
            compose('stop', 'alloy')
            request(emitter, json.dumps({'message': 'while-stopped-' + PROJECT}).encode())
            compose('start', 'alloy')
            wait_for('Alloy offset resume', lambda: entries('while-stopped-' + PROJECT))
            positions = compose('exec', '-T', 'alloy', 'sh', '-c',
                                r'find /var/lib/alloy -name positions.yml -exec cat {} \;')
            assert 'positions:' in positions and '-json.log' in positions, positions
            assert len(entries(record['message'])) == len(found)
            passed('Alloy persisted positions and resumed logs written during its stop')

            compose('up', '-d', '--force-recreate', '--no-deps', 'emitter')
            emitter = address('emitter', 8000)
            wait_for('recreated backend emitter', lambda: request(emitter) == '')
            request(emitter, json.dumps({'message': 'new-container-' + PROJECT}).encode())
            wait_for('new Docker container ID discovery', lambda: entries('new-container-' + PROJECT))
            passed('Alloy discovers a recreated backend without a hardcoded container ID')

            compose('stop', 'loki')
            request(emitter, json.dumps({'message': 'loki-outage-' + PROJECT}).encode())
            time.sleep(6)
            compose('start', 'loki')
            loki = address('loki', 3100)
            wait_for('Loki outage recovery', lambda: entries('loki-outage-' + PROJECT))
            passed('Alloy retries delivery across a short Loki outage')

            compose('up', '-d', '--force-recreate', '--no-deps', 'loki')
            loki = address('loki', 3100)
            wait_for('recreated Loki readiness', lambda: 'ready' in request(loki + '/ready'))
            wait_for('Loki persistent log data', lambda: entries(record['message']))
            config_text = request(loki + '/config')
            assert re.search(r'retention_period: (72h|3d)', config_text), 'Dev retention was not applied'
            assert 'retention_enabled: true' in config_text
            passed('Loki data survives container recreation; compactor retention active at 72h')
            REPORT.parent.mkdir(parents=True, exist_ok=True)
            REPORT.write_text(json.dumps({'checked_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'scope': 'isolated local Docker; synthetic app logs and OTLP trace; not EC2 deployment or browser UI',
                'checks': checks, 'images': {k: v['image'] for k, v in services.items()},
                'production_deployed': False}, ensure_ascii=False, indent=2) + '\n')
            print('Report: ' + str(REPORT), flush=True)
        except Exception:
            print(compose('logs', '--no-color', '--tail', '35', 'alloy', 'loki', 'grafana'), flush=True)
            raise
        finally:
            compose('down', '--volumes', '--remove-orphans')


if __name__ == '__main__':
    main()
