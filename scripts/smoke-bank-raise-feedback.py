"""Pinned two-hand raise-feedback startup: two questions, six grades, mixed reviews and OpenAPI."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PREFIX = '/api/v1/trainer/research/bank-raise-feedback-drill'
BASE = 'http://localhost:18080' + PREFIX
STARTUP_SECONDS = 600
FIXTURE = json.loads((ROOT / 'docs/data/supplied-bank-raise-drill-fixture.json').read_bytes())
PIN = FIXTURE['snapshot']['studyHash']
METADATA = dict(schemaVersion=FIXTURE['snapshot']['schemaVersion'], studyHash=PIN,
                publicationStatus='BANK_RAISE_MINIMA_TWO_HAND_FEEDBACK_RESEARCH_ONLY',trainerAdmission=False,
                model='SUPPLIED_JOINT_PREFLOP_SELECTED_FLOP_SIZE_MENU/v1',evScope=FIXTURE['snapshot']['evScope'],
                tableSeats=6,activePlayers=2,availableQuestions=2,cachedLegalGrades=6,maximumAttempts=128,sourceCaseIndices=[0,2],
                startupBudget=dict(physicalGames=1,maximumParentLpSolves=4,maximumEndpointLpSolves=2,freshReferenceRuns=2,freshIterations=240000))


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
                assert response.headers.get('Content-Disposition') == 'attachment; filename="pokerlab-bank-raise-feedback-review-' + PIN[:12] + '.json"'
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
        print('Root preflop routes absent without opt-in')
        return
    assert wait_for_metadata() == METADATA
    with urllib.request.urlopen(BASE.removesuffix(PREFIX) + '/v3/api-docs', timeout=20) as response:
        docs = json.load(response)
    schemas = docs['components']['schemas']
    for route, name, fields in [('grade', 'BankRaiseGradeRequest', {'studyHash', 'sourceCaseIndex', 'action'}),
                                ('review', 'BankRaiseReviewRequest', {'studyHash', 'choices'}),
                                ('review/export', 'BankRaiseReviewRequest', {'studyHash', 'choices'})]:
        request = docs['paths'][PREFIX + '/' + route]['post']['requestBody']
        assert request['required'] is True
        assert request['content']['application/json']['schema']['$ref'] == '#/components/schemas/' + name
        assert set(schemas[name]['properties']) == fields
    assert schemas['BankRaiseGradeRequest']['properties']['sourceCaseIndex']['maximum'] == 2
    assert schemas['BankRaiseSubmittedChoice']['properties']['sourceCaseIndex']['maximum'] == 2
    assert not any(n.startswith('SevensPreflop') for n in schemas)
    for q in FIXTURE['snapshot']['questions']:
        public = call('/questions/' + str(q['sourceCaseIndex']) + '?studyHash=' + PIN)
        assert public == q
        assert public['board'] == [] and public['evScope'] == 'CURRENT_DECISION_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
        assert public['context']['actingSeat'] == 'BB' and public['context']['toCallBb'] == 2.0
        assert public['context']['potBb'] == 4.5 and len(public['context']['players']) == 6
        for hidden in ['worlds', 'dealtCombos', 'opponentCards', 'candidate', 'lowerWitness', 'informationSet', '6d', 'Qc', 'Td']:
            assert '"' + hidden + '"' not in json.dumps(public)
    choices = []
    for grade in FIXTURE['grades']:
        choice = dict(sourceCaseIndex=grade['sourceCaseIndex'], action=grade['move']['action'])
        assert call('/grade', dict(studyHash=PIN, **choice)) == grade
        choices.append(choice)
    fixture_choices = [dict(sourceCaseIndex=2, action='call'), dict(sourceCaseIndex=0, action='raise:9.0'), dict(sourceCaseIndex=2, action='fold'), dict(sourceCaseIndex=2, action='raise:9.0')]
    for ordered in [fixture_choices, choices, choices[::-1] + choices[:1], [], [choices[0]] * 128]:
        body = dict(studyHash=PIN, choices=ordered)
        expected = canonical_review(ordered)
        assert call('/review', body) == expected
        export=call('/review/export', body, raw=True)
        assert json.loads(export) == expected
        assert export == call('/review/export', body, raw=True)
    for changed in [dict(choices[0], sourceCaseIndex=3), dict(choices[0], sourceCaseIndex=1), dict(choices[0], sourceCaseIndex='0'),
                    dict(choices[0], sourceCaseIndex=.5), dict(choices[0], action='check'),
                    dict(choices[0], evBb=99)]:
        call('/grade', dict(studyHash=PIN, **changed), 400)
    call('/grade', dict(studyHash='0' * 64, **choices[0]), 400)
    call('/questions/0?studyHash=' + '0' * 64, None, 400)
    call('/questions/3?studyHash=' + PIN, None, 400)
    call('/questions/1?studyHash=' + PIN, None, 400)
    call('/review', dict(studyHash=PIN, choices=[choices[0]] * 129), 400)
    call('/review/export', dict(studyHash=PIN, choices=[None]), 400)
    print('Owned raise feedback startup,2 questions,6 grades,mixed ordered/repeated/max reviews,exports and strict controls passed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--disabled', action='store_true')
    run(parser.parse_args().disabled)
