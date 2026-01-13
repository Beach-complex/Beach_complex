-- 부산 해수욕장 초기 데이터

INSERT INTO beaches (code, name, status, location) VALUES
    ('HAEUNDAE', '해운대해수욕장', 'OPEN', ST_SetSRID(ST_MakePoint(129.1599, 35.1587), 4326)),
    ('GWANGALLI', '광안리해수욕장', 'OPEN', ST_SetSRID(ST_MakePoint(129.1185, 35.1532), 4326)),
    ('SONGJEONG', '송정해수욕장', 'OPEN', ST_SetSRID(ST_MakePoint(129.1997, 35.1783), 4326)),
    ('DADAEPO', '다대포해수욕장', 'OPEN', ST_SetSRID(ST_MakePoint(128.9657, 35.0461), 4326)),
    ('SONGDO', '송도해수욕장', 'OPEN', ST_SetSRID(ST_MakePoint(129.0153, 35.0757), 4326)),
    ('ILGWANG', '일광해수욕장', 'OPEN', ST_SetSRID(ST_MakePoint(129.2344, 35.2603), 4326)),
    ('IMRANG', '임랑해수욕장', 'OPEN', ST_SetSRID(ST_MakePoint(129.2675, 35.3032), 4326))
ON CONFLICT (code) DO NOTHING;