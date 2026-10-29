"""Real pinned BTN startup: four public tables, eight grades, ordered reviews and OpenAPI."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PREFIX = '/api/v1/trainer/research/size-menu-btn-drill'
BASE = 'http://localhost:18080' + PREFIX
STARTUP_SECONDS = 600
FIXTURE = json.loads((ROOT / 'frontend/src/test-data/size-menu-btn-drill.json').read_bytes())
PIN = FIXTURE['metadata']['studyHash']


def call(path='', body=None, expected_status=200, raw=False):
    request = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(),
                                     headers={'Content-Type': 'application/json'})
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        assert response.status == expected_status, (path, response.status, expected_status)
        data = response.read()
        if expected_status == 200:
            assert 'no-store' in response.headers.get('Cache-Control', '')
            if path == '/review/export':
                assert response.headers.get('Content-Disposition') == 'attachment; filename="pokerlab-btn-sizing-review-' + PIN[:12] + '.json"'
                assert data.endswith(b'\n') and b'\r' not in data
        return data if raw else json.loads(data)


def wait_for_metadata(timeout=STARTUP_SECONDS):
    deadline = time.monotonic() + timeout
    while True:
        try:
            return call()
        except (urllib.error.URLError, TimeoutError, ConnectionError, AssertionError):
            if time.monotonic() >= deadline:
                raise
            time.sleep(2)


def canonical_review(choices):
    assert len(choices) <= 128
    attempts = []
    counts = dict(distinctQuestions=len({c['sourceCaseIndex'] for c in choices}), withinLimit=0, outsideLimit=0, strategyDependent=0)
    fields = dict(WITHIN_LIMIT='withinLimit', OUTSIDE_LIMIT='outsideLimit', STRATEGY_DEPENDENT='strategyDependent')
    for choice in choices:
        assert set(choice) == {'sourceCaseIndex', 'action'} and type(choice['sourceCaseIndex']) is int
        matches = [g for g in FIXTURE['grades'] if g['sourceCaseIndex'] == choice['sourceCaseIndex'] and g['move']['action'] == choice['action']]
        assert len(matches) == 1
        grade = matches[0]
        attempts.append(dict(ordinal=len(attempts), attempt=choice, grade=grade))
        counts[fields[grade['move']['classification']]] += 1
    return dict(studyHash=PIN, attempts=attempts, counts=counts)


def run(disabled=False):
    if disabled:
        for path, body in [('', None), ('/questions/0?studyHash=' + PIN, None), ('/grade', {}), ('/review', {}), ('/review/export', {})]:
            call(path, body, 404)
        print('BTN sizing routes absent without opt-in')
        return
    assert wait_for_metadata() == FIXTURE['metadata']
    with urllib.request.urlopen(BASE.removesuffix(PREFIX) + '/v3/api-docs', timeout=20) as response:
        docs = json.load(response)
    schemas = docs['components']['schemas']
    for route, name, fields in [('grade', 'SizeMenuBtnGradeRequest', {'studyHash', 'sourceCaseIndex', 'action'}),
                                ('review', 'SizeMenuBtnReviewRequest', {'studyHash', 'choices'}),
                                ('review/export', 'SizeMenuBtnReviewRequest', {'studyHash', 'choices'})]:
        request = docs['paths'][PREFIX + '/' + route]['post']['requestBody']
        assert request['required'] is True
        assert request['content']['application/json']['schema']['$ref'] == '#/components/schemas/' + name
        assert set(schemas[name]['properties']) == fields
    for q in FIXTURE['questions']:
        public = call('/questions/' + str(q['sourceCaseIndex']) + '?studyHash=' + PIN)
        assert public == q
        assert public['context']['actingSeat'] == 'BTN' and public['context']['toCallBb'] == 1.625
        assert public['context']['potBb'] == 8.125 and len(public['context']['players']) == 6
        for hidden in ['worlds', 'dealtCombos', 'opponentCards', 'candidate', 'lowerWitness']:
            assert '"' + hidden + '"' not in json.dumps(public)
    choices = []
    for grade in FIXTURE['grades']:
        choice = dict(sourceCaseIndex=grade['sourceCaseIndex'], action=grade['move']['action'])
        assert call('/grade', dict(studyHash=PIN, **choice)) == grade
        choices.append(choice)
    for ordered in [choices, choices[::-1] + choices[:1], [], [choices[0]] * 128]:
        body = dict(studyHash=PIN, choices=ordered)
        expected = canonical_review(ordered)
        assert call('/review', body) == expected
        assert json.loads(call('/review/export', body, raw=True)) == expected
    assert canonical_review(choices) == FIXTURE['review']
    for changed in [dict(choices[0], sourceCaseIndex=4), dict(choices[0], sourceCaseIndex='0'),
                    dict(choices[0], sourceCaseIndex=.5), dict(choices[0], action='check'),
                    dict(choices[0], evBb=99)]:
        call('/grade', dict(studyHash=PIN, **changed), 400)
    call('/grade', dict(studyHash='0' * 64, **choices[0]), 400)
    call('/questions/0?studyHash=' + '0' * 64, None, 400)
    call('/questions/4?studyHash=' + PIN, None, 400)
    call('/review', dict(studyHash=PIN, choices=[choices[0]] * 129), 400)
    call('/review/export', dict(studyHash=PIN, choices=[None]), 400)
    print('Owned BTN startup,4 questions,8 grades,ordered/repeated/max reviews,exports and strict controls passed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--disabled', action='store_true')
    run(parser.parse_args().disabled)
