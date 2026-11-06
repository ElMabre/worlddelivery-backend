"""Static checks only. Does not call AWS, Docker, systemd or read credentials."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parent
for filename in ('frontend-stack.json', 'api-stack.json'):
    template = json.loads((root / filename).read_text(encoding='utf-8'))
    known = set(template['Resources']) | set(template.get('Parameters', {}))

    def check_ref(value):
        name = value.split('.')[0]
        assert name in known or name.startswith('AWS::'), f'{filename}: unknown reference {value}'

    def visit(value):
        if isinstance(value, dict):
            if 'Ref' in value:
                check_ref(value['Ref'])
            if 'Fn::GetAtt' in value:
                check_ref(value['Fn::GetAtt'][0])
            if 'Fn::Sub' in value:
                for name in re.findall(r'\$\{([^}]+)\}', value['Fn::Sub']):
                    check_ref(name)
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)

    visit(template)
    print(f'{filename}: JSON and references OK')

api = json.loads((root / 'api-stack.json').read_text(encoding='utf-8'))['Resources']
routes = [r['Properties'] for r in api.values() if r['Type'] == 'AWS::ApiGatewayV2::Route']
assert len(routes) == 17
assert all(r['AuthorizationType'] == 'JWT' for r in routes)
assert all(r['AuthorizationScopes'] == ['resource-server-worlddelivery/admin'] for r in routes if '/rabbitmq/' in r['RouteKey'] or '/usuarios' in r['RouteKey'])
assert api['Integration']['Properties']['RequestParameters'] == {'overwrite:path': '$request.path'}
assert {'PUT', 'PATCH', 'DELETE', 'OPTIONS'} <= set(api['Api']['Properties']['CorsConfiguration']['AllowMethods'])
site = json.loads((root / 'frontend-stack.json').read_text(encoding='utf-8'))['Resources']
errors = site['Distribution']['Properties']['DistributionConfig']['CustomErrorResponses']
assert {e['ErrorCode'] for e in errors} == {403, 404}
assert all(e['ResponseCode'] == 200 and e['ResponsePagePath'] == '/index.html' for e in errors)
for name in ('ms-pedidos', 'ms-operaciones', 'ms-rabbit-admin', 'ms-usuarios'):
    unit = (root / 'systemd' / f'{name}.service').read_text(encoding='utf-8')
    assert '-Xmx256m' in unit
    assert f'EnvironmentFile=/etc/worlddelivery/{name}.env' in unit
    assert f'ReadWritePaths=/var/lib/worlddelivery/{name}' in unit
print('Routes, scopes, CORS, SPA fallback and service limits OK. AWS/Linux validation still pending.')
