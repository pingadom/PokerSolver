"""Check default absence or all four BTN questions after the explicit long startup replay."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

BASE = 'http://localhost:18080/api/v1/trainer/research/btn-flop-interval'
PIN = '9686d76c4a9d3b1a0875f7fcaed3edecf4491e0c24698892b516f91a69c89cd1'
STARTUP_SECONDS = 900


def call(path='', body=None, expected_status=200):
    request = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(),
                                     headers={'Content-Type': 'application/json'})
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        assert response.status == expected_status, (path, response.status, expected_status)
        result = json.load(response)
        if expected_status == 200:
            assert 'no-store' in response.headers.get('Cache-Control', '')
        return result


def wait_for_metadata(timeout=STARTUP_SECONDS):
    deadline = time.monotonic() + timeout
    while True:
        try:
            return call()
        except (urllib.error.URLError, TimeoutError, ConnectionError, AssertionError):
            if time.monotonic() >= deadline:
                raise
            time.sleep(2)


def run(disabled):
    if disabled:
        for path, body in [('', None), ('/questions/0?reportHash=' + PIN, None), ('/grade', {}), ('/review', {}), ('/review/export', {})]:
            call(path, body, 404)
        print('BTN interval trainer is absent without opt-in')
        return
    metadata = wait_for_metadata()
    assert metadata['reportHash'] == PIN and metadata['availableQuestions'] == 4
    assert metadata['activePlayers'] == 2 and metadata['maximumAttempts'] == 100 and metadata['trainerAdmission'] is False
    assert metadata['sourceHistoryReachStatus'] == 'UNAVAILABLE_NO_SOURCE_POLICY'
    docs_url = BASE.removesuffix('/api/v1/trainer/research/btn-flop-interval') + '/v3/api-docs'
    with urllib.request.urlopen(docs_url, timeout=20) as response:
        docs = json.load(response)
    schemas = docs['components']['schemas']
    prefix = '/api/v1/trainer/research/btn-flop-interval'
    for route, name, fields in [('grade', 'BtnFlopIntervalGradeRequest', {'reportHash', 'questionIndex', 'action'}),
                                ('review', 'BtnFlopIntervalReviewRequest', {'reportHash', 'choices'}),
                                ('review/export', 'BtnFlopIntervalReviewRequest', {'reportHash', 'choices'})]:
        request = docs['paths'][prefix + '/' + route]['post']['requestBody']
        assert request['required'] is True
        assert request['content']['application/json']['schema']['$ref'] == '#/components/schemas/' + name
        assert set(schemas[name]['properties']) == fields
    assert schemas['BtnFlopIntervalGradeRequest']['properties']['questionIndex']['type'] == 'integer'
    assert set(schemas['BtnFlopIntervalChoice']['properties']) == {'questionIndex', 'action'}
    assert schemas['BtnFlopIntervalReviewRequest']['properties']['choices']['items']['$ref'] == '#/components/schemas/BtnFlopIntervalChoice'
    expected_bytes = (Path(__file__).resolve().parents[1] / 'docs/data/supplied-four-button-hand-btn-flop-drill.json').read_bytes()
    expected = json.loads(expected_bytes)
    for q in expected['questions']:
        assert call('/questions/' + str(q['questionIndex']) + '?reportHash=' + PIN) == q
    choices = []
    for a in expected['attempts']:
        choice = dict(questionIndex=a['questionIndex'], action=a['action'])
        assert call('/grade', dict(reportHash=PIN, **choice)) == a['feedback']
        choices.append(choice)
    assert call('/review', dict(reportHash=PIN, choices=choices)) == expected
    request = urllib.request.Request(BASE + '/review/export',
                                     data=json.dumps(dict(reportHash=PIN, choices=choices)).encode(),
                                     headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=20) as response:
        assert response.read() == expected_bytes
        assert 'no-store' in response.headers.get('Cache-Control', '')
        assert response.headers.get_content_type() == 'application/json'
        assert 'pokerlab-btn-flop-review-' + PIN[:12] + '.json' in response.headers.get('Content-Disposition', '')
    assert call('/review', dict(reportHash=PIN, choices=[]))['counts']['distinctQuestionsAttempted'] == 0
    repeated = call('/review', dict(reportHash=PIN, choices=[choices[4]] * 100))
    assert repeated['counts'] == dict(distinctQuestionsAttempted=1, withinLimit=100, outsideLimit=0, strategyDependent=0)
    for body in [dict(reportHash='0' * 64, **choices[0]), dict(reportHash=PIN, questionIndex=4, action='bet'),
                 dict(reportHash=PIN, questionIndex=0, action='call'), dict(reportHash=PIN, **choices[0], evLossBb=0)]:
        assert call('/grade', body, 400)['code'] == 'INVALID_REQUEST'
    assert call('/review', dict(reportHash=PIN, choices=[choices[0]] * 101), 400)['code'] == 'INVALID_REQUEST'
    for body in [{}, dict(reportHash='0' * 64, choices=[]), dict(reportHash=PIN, choices=[choices[0]] * 101),
                 dict(reportHash=PIN, choices=[dict(questionIndex=0, action='bet', evLossBb=0)])]:
        assert call('/review/export', body, 400)['code'] == 'INVALID_REQUEST'
    print('Complete pinned BTN replay, four questions, eight grades, exact canonical export, mixed review, typed OpenAPI, caps and rejections passed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--disabled', action='store_true')
    parser.add_argument('--base-url', default='http://localhost:18080')
    args = parser.parse_args()
    BASE = args.base_url.rstrip('/') + '/api/v1/trainer/research/btn-flop-interval'
    run(args.disabled)
