# 관측 로그 파이프라인 (PR4)

[이슈 #233](https://github.com/Beach-complex/Beach_complex/issues/233)의 PHJ2000 담당 범위다.
앱 서버의 Docker `json-file` 로그를 Alloy가 읽어 관측 서버 Loki로 보내고, Grafana에서 로그와 Tempo Trace를 연결한다.
Trace는 기존 경로대로 애플리케이션에서 Tempo로 직접 전송한다.

```text
앱 EC2: Backend stdout → Docker json-file → Alloy ── TCP 3100 ──→ 관측 EC2: Loki
         └────────────────── OTLP 4317/4318 ──────────────────→ Tempo
                                                                 ↑
                                                            Grafana 조회
```

## 구성과 저장 정책

| 구성 | 파일 / 동작 |
|---|---|
| Loki | `compose/loki/local-config.yaml`, 단일 프로세스, TSDB v13 + filesystem |
| 저장소 | 관측 EBS의 `/opt/beach-observability/data/loki` → `/loki`; chunk/index/WAL/compactor 상태 포함 |
| 보존 기간 | Terraform `env`: dev 72h, staging 168h, prod 336h; compactor 활성화, 삭제 지연 2h |
| 접근 | 관측 SG TCP 3100은 앱 SG만 허용. 인증 없는 Loki를 인터넷에 직접 공개하지 않는다. |
| 수집 | `app-agent/alloy/config.alloy`, Docker 로그 디렉터리 읽기 전용; Docker socket 사용 없음 |
| 수집 대상 | Docker logging attribute `beach.logs=backend`인 컨테이너만 통과 |
| 재배포 | 기본 200ms 주기로 파일 탐색. 컨테이너 ID를 설정에 고정하지 않는다. |
| 읽기 위치 | 파일 지문과 읽기 위치를 앱 서버 `alloy-data` Docker 볼륨에 저장. `down -v`로 삭제하지 않는다. |
| 회전 | backend Docker 로그 파일당 50MB, 최대 5개, 압축하지 않음 |
| 인덱스 라벨 | `app=beach-complex`, `env`, `service=backend`, `host`만 유지 |
| 식별자 | traceId/spanId/requestId/userId/notificationId/outboxEventId는 JSON 본문에 유지; 인덱스 라벨로 승격하지 않는다. |

Alloy는 root 소유 Docker 파일을 읽기 위해 UID 0으로 실행하지만, capability를 모두 제거하고 `no-new-privileges`를 사용한다.
빈 상태 볼륨에 이미지 내부 UID 소유권이 복사되지 않도록 `nocopy: true`를 지정한다.
이는 rootful Docker의 기본 `json-file` 배치를 기준으로 한다. rootless Docker나 user namespace remapping 환경은 별도 권한 검증이 필요하다.

`otelcol.receiver.filelog`와 `otelcol.storage.file`은 고정한 Alloy v1.19.2에서 공개 미리보기 기능이다.
Compose와 설정 검사에 `--stability.level=public-preview`를 명시했다. 업그레이드할 때 같은 회귀 테스트를 실행한다.
Docker 레코드 끝의 시각까지 비교하도록 지문 크기를 128KiB로 설정했다. 긴 로그 조각의 앞부분이 같아도 다른 파일로 구분한다.
파일 지문으로 이름이 바뀐 파일을 추적하고 `.log.4`부터 `.log`까지 차례로 읽는다.
Docker가 나눈 한 줄은 컨테이너 ID와 stdout/stderr를 기준으로 묶어, 줄바꿈이 나올 때까지 구분자 없이 합친다.

이 버전의 Alloy 정렬 설정은 `lexicographic`을 받지만 내부 Stanza는 `alphabetical`을 요구한다.
이를 피하려고 `operators`의 `file_input`에 정렬을 직접 설정했다. 바깥쪽 입력은 사용하지 않는 경로를 가리킨다.
두 입력이 같은 파일을 읽지 않으며, 실제 입력은 같은 file storage에 읽기 위치를 저장한다.
이 우회 설정은 해당 버전의 매핑이 수정되면 제거할 수 있다.

Alloy의 읽기 위치는 지속되지만 전송 성공 확인과 원자적으로 묶이지 않는다. 짧은 Loki 장애에는 재시도하며,
장시간 장애·강제 종료·로그 회전 한도 초과에 대한 무손실 또는 exactly-once를 보장하지 않는다.
재조립 중인 조각은 메모리에만 있다. 조각 사이에 5초 이상 지연되거나 재조립 도중 Alloy가 종료되면 완전한 한 줄을 보장하지 않는다.
한 줄의 크기는 Loki 기본 상한인 256KiB 이내로 유지한다. 회전 파일은 최대 5개를 수집하므로 Docker의 보존 개수와 압축 설정을 함께 유지한다.
기존 `loki.source.file`의 positions 파일은 새 저장 형식으로 자동 이관하지 않는다. 이전 초안을 실행한 환경에 적용하면 남아 있는 로그를 다시 읽을 수 있다.
보존 기간은 디스크 용량 상한이 아니며, 오래된 chunk 삭제는 compactor 주기와 삭제 지연 이후에 이루어진다.

## 로컬 검증

필요 도구: Docker Engine + Compose v2 이상, Python 3, Terraform 1.15.8, cloud-init.
Terraform 테스트는 mock AWS provider를 사용하므로 AWS 자격증명이나 실제 리소스가 필요하지 않다.

저장소 루트에서 실행한다. 복사 가능한 명령은 [verify-local.txt](verify-local.txt)에도 있다.

```bash
bash deploy/observability/scripts/validate-iac.sh
python3 deploy/observability/scripts/verify-log-pipeline.py
```

첫 명령은 Terraform fmt/init/validate와 mock plan 3건(dev/staging/prod), cloud-init schema 및 EC2 user data 16KB 제한,
Compose 병합, Loki/Alloy 실제 이미지의 설정 검증을 수행한다.

두 번째 명령은 임의의 localhost 포트, 전용 Compose 프로젝트와 볼륨을 만든다. 합성 JSON 로그와 OTLP span으로 다음을 확인하고 자체 리소스를 정리한다.

- 실제 Docker json-file → Alloy → Loki 수집, 원본 JSON 보존 및 traceId 검색
- 다른 컨테이너 제외, `/series` 기준 인덱스 라벨 4개만 존재
- Grafana provisioning의 변수·정규식과 datasource health, Grafana proxy를 통한 Trace 및 역방향 로그 조회
- Alloy 정상 재시작 후 저장된 파일 지문·읽기 위치 사용 및 중지 중 쌓인 로그 수집
- 두 컨테이너의 stdout/stderr에서 조각이 교차하는 40KB JSON의 원문 보존과 traceId 검색
- Alloy 중지 중 회전된 로그 110건과 파일 경계에 걸친 90KB JSON 복구, 재시작 후 중복 전송 검사
- backend 컨테이너 교체 시 새 로그 경로 발견, 짧은 Loki 중단 후 전송 재시도
- Loki 컨테이너 재생성 후 기존 데이터 조회 및 실제 적용된 retention 설정

기존 `beach.logs=backend` 컨테이너가 있으면 중지된 컨테이너도 포함하여 테스트가 실행을 거부한다.
실제 앱 로그와 합성 테스트가 섞이지 않도록 별도 Docker daemon을 사용한다.
보고서는 `build/reports/observability/log-pipeline.json`에 생성하며 CI에서도 같은 검증을 실행하도록 구성했다.

이 검증은 합성 데이터와 로컬 볼륨을 사용한다. 실제 EC2/EBS 부팅·재부팅, 브라우저 클릭,
운영 앱의 로그와 span 연결, 보존 기간 경과에 따른 실제 삭제는 서버 적용 후 확인할 항목이다.
24시간 운영 검증, 대시보드·알림·SLI/SLO 및 Terraform 원격 상태 저장소는 #233에서 분리한 후속 범위다.

## 서버 적용용 설정 (이번 작업에서는 실행하지 않음)

관측 서버 Terraform은 검증된 EBS 마운트 아래에 Loki 데이터 디렉터리(UID/GID 10001)를 만들고,
설정 및 환경별 `LOKI_RETENTION_PERIOD`를 배치한다. 기존 마운트 가드를 유지한다.
현재 모듈의 `user_data_replace_on_change = true` 때문에 이 변경의 apply는 관측 EC2 교체를 유발할 수 있다.
적용 전 실제 state 기준 plan의 교체 대상과 EBS 보존 여부를 확인해야 한다.

앱 서버에서는 다음 값을 `app-agent/.env.example`에 따라 설정한다.

```dotenv
LOKI_URL=http://<관측-서버-private-ip>:3100/loki/api/v1/push
APP_ENVIRONMENT=dev
LOG_HOST=beach-app-ec2
DOCKER_LOG_ROOT=/var/lib/docker/containers
```

`LOG_HOST`는 컨테이너 ID가 아닌 안정적인 서버 식별자다. 경로는 앱 서버에서 `docker info --format '{{.DockerRootDir}}'`로 확인한다.
실제 앱 Compose의 서비스가 `app`인지 확인한 뒤 `backend-logging.override.yml`을 함께 사용한다.
서비스명이 다르면 override의 서비스 키를 실제 이름으로 맞춘다.
로그 속성은 컨테이너 생성 때 적용되므로 기존 컨테이너에는 재생성이 필요하다.

```bash
# 앱 서버, 실제 기존 Compose 파일·환경 파일을 그대로 지정해서 병합 결과를 먼저 확인한다.
docker compose -f <기존-compose.yml> -f <경로>/app-agent/backend-logging.override.yml config
# 이후 배포 단계에서만 app을 재생성하고 Alloy를 기동한다.
docker compose -f <기존-compose.yml> -f <경로>/app-agent/backend-logging.override.yml up -d --no-deps app
docker compose --env-file <경로>/app-agent/.env -f <경로>/app-agent/docker-compose.yml up -d
```

지속 배포에 사용하는 Compose에도 logging 설정이 남아 있어야 한다. 저장소의 `deploy/docker-compose.ec2.yml.example`에도 같은 설정을 추가했다.
`APP_ENVIRONMENT`는 Loki 라벨이며 Spring profile을 변경하지 않는다. 현재 Logback의 prod profile이 NDJSON을 출력한다.
JSON 검색을 확인할 때는 실제 앱 출력 형식을 먼저 확인하고, 수집 때문에 애플리케이션 profile을 임의로 바꾸지 않는다.

Grafana Explore → Loki에서 다음을 조회한다.

```logql
{app="beach-complex", env="dev", service="backend"} | json
{app="beach-complex", env="dev", service="backend"} | json | traceId="<32자리 trace ID>"
```

로그의 `View trace` 링크는 Tempo를 가리킨다. JSON `traceId`와 현재 개발용 텍스트의 `traceId=...`를 지원한다.
Tempo의 Trace to logs는 span 시간 전후 5분에서 동일 traceId 문자열을 검색한다.
Trace가 샘플링되지 않았거나 Tempo 보존 기간이 지났으면 해당 로그의 링크 대상은 없을 수 있다.

## 근거

- [ADR-014: Loki 및 보존·라벨 정책](https://github.com/Beach-complex/beach_complex_docs/blob/main/adr/ADR-014-logging-storage-backend.md)
- [ADR-015: Alloy 및 file tail](https://github.com/Beach-complex/beach_complex_docs/blob/main/adr/ADR-015-log-collector.md)
- [Alloy filelog receiver](https://grafana.com/docs/alloy/latest/reference/components/otelcol/otelcol.receiver.filelog/)
- [Alloy file storage](https://grafana.com/docs/alloy/latest/reference/components/otelcol/otelcol.storage.file/)
- [Stanza 재조립 설정](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.158.0/pkg/stanza/docs/operators/recombine.md)
- [Alloy Loki write 및 재시도](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.write/)
- [Loki retention](https://grafana.com/docs/loki/latest/operations/storage/retention/)
- [Grafana Tempo와 로그 연결](https://grafana.com/docs/grafana/latest/datasources/tempo/configure-tempo-data-source/)
