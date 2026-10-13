"""Check default-disabled routes or the real pinned interval drill in the Compose API."""

import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

BASE = 'http://localhost:18080/api/v1/trainer/research/later-flop-interval'
PIN = '4526ff928e05bc0e962d78661a19b82cb127d5e058f7a25bb8fd1e393c51f7ea'


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
        for path, body in [('', None), ('/question', None), ('/grade', {}), ('/review', {})]:
            call(path, body, 404)
        print('Physical flop interval routes are absent without explicit opt-in')
        return
    metadata = wait_for_metadata()
    assert metadata['reportHash'] == PIN
    assert metadata['availableQuestions'] == 1 and metadata['activePlayers'] == 2
    assert metadata['trainerAdmission'] is False
    assert metadata['sourceHistoryReachStatus'] == 'UNAVAILABLE_NO_SOURCE_POLICY'
    fixture = Path(__file__).resolve().parents[1] / 'docs/data/supplied-four-button-hand-later-bb-flop-drill.json'
    expected = json.loads(fixture.read_text(encoding='utf-8'))
    question = call('/question')
    assert question == expected['question']
    actions = [attempt['action'] for attempt in expected['attempts']]
    for attempt in expected['attempts']:
        assert call('/grade', {'reportHash': PIN, 'action': attempt['action']}) == attempt['feedback']
    assert call('/review', {'reportHash': PIN, 'actions': actions}) == expected
    for body in [{'reportHash': '0' * 64, 'action': 'fold'},
                 {'reportHash': PIN, 'action': 'check'},
                 {'reportHash': PIN, 'action': 'fold', 'evLossBb': 0}]:
        assert call('/grade', body, 400)['code'] == 'INVALID_REQUEST'
    assert call('/review', {'reportHash': PIN, 'actions': ['fold'] * 101}, 400)['code'] == 'INVALID_REQUEST'
    print('Pinned physical startup, canonical question, all interval grades, saved review and invalid requests passed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--disabled', action='store_true')
    run(parser.parse_args().disabled)
