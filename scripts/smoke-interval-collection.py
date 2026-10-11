"""Check default-disabled collection routes or the real four-question Compose API."""

import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

BASE = 'http://localhost:18080/api/v1/trainer/research/interval-collection'
PIN = '227fd33b33ce8ed8be46af3d0d4515e4c7b017c3468ec6f9a025bf96d67070b2'


def call(path='', body=None, expected_status=200):
    request = urllib.request.Request(BASE + path,
        data=None if body is None else json.dumps(body).encode(),
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


def wait_for_metadata(timeout=180):
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
        for path, body in [('', None), ('/questions/0?collectionHash=' + PIN, None),
                           ('/grade', {}), ('/review', {})]:
            call(path, body, 404)
        print('Collection routes are absent without explicit opt-in')
        return
    metadata = wait_for_metadata()
    assert metadata['collectionHash'] == PIN and metadata['availableQuestions'] == 4
    assert metadata['activePlayers'] == 2 and metadata['maximumAttempts'] == 100
    assert metadata['trainerAdmission'] is False
    assert metadata['selection'] == 'ORDERED_DISTINCT_QUALIFIED_INTERVAL_QUESTIONS/v1'
    fixture = Path(__file__).resolve().parents[1] / 'docs/data/supplied-four-question-interval-collection-review.json'
    expected = json.loads(fixture.read_text(encoding='utf-8'))
    for question in expected['questions']:
        assert call('/questions/' + str(question['index']) + '?collectionHash=' + PIN) == question
        assert len(question['decision']['table']['players']) == 6
        assert 'moves' not in question['decision'] and 'prior' not in question['decision']
    choices = []
    for attempt in expected['attempts']:
        choice = dict(questionIndex=attempt['questionIndex'],
                      reportHash=attempt['feedback']['decision']['reportHash'], action=attempt['action'])
        assert call('/grade', dict(collectionHash=PIN, **choice)) == attempt['feedback']
        choices.append(choice)
    assert call('/review', dict(collectionHash=PIN, choices=choices)) == expected
    repeated = call('/review', dict(collectionHash=PIN, choices=[choices[0]] * 100))
    assert repeated['counts']['distinctQuestionsAttempted'] == 1
    for body in [dict(collectionHash='0' * 64, **choices[0]),
                 dict(collectionHash=PIN, **dict(choices[0], reportHash=choices[1]['reportHash'])),
                 dict(collectionHash=PIN, **dict(choices[0], action='check')),
                 dict(collectionHash=PIN, **choices[0], evLossBb=0)]:
        assert call('/grade', body, 400)['code'] == 'INVALID_REQUEST'
    assert call('/review', dict(collectionHash=PIN, choices=[choices[0]] * 101), 400)['code'] == 'INVALID_REQUEST'
    print('Four pinned physical replays, distinct questions, all verdicts, canonical review and invalid requests passed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--disabled', action='store_true')
    parser.add_argument('--base-url', default='http://localhost:18080')
    args = parser.parse_args()
    BASE = args.base_url.rstrip('/') + '/api/v1/trainer/research/interval-collection'
    run(args.disabled)
