"""Independent fixed two-context flop collection: source physics/certificates and exact review."""
import argparse
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    result = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = result
    spec.loader.exec_module(result)
    return result

initial = module('flop_collection_initial', 'check-supplied-flop-feedback.py')
later = module('flop_collection_later', 'check-later-bb-flop-drill.py')
require = later.require
SCHEMA = 'pokerlab-flop-interval-collection-review/v1'
SELECTION = 'ORDERED_INITIAL_AND_LATER_BB_FLOP_QUESTIONS/v1'
KINDS = ['INITIAL_CHECK_BET', 'BB_FACING_BET']
PINS = [initial.REPORT_HASH, later.feedback.REPORT_HASH]
IDENTITY = dict(schemaVersion=SCHEMA, selection=SELECTION,
                sources=[dict(kind=k, reportHash=p) for k,p in zip(KINDS,PINS,strict=True)])
PIN = hashlib.sha256(json.dumps(IDENTITY, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
DRILLS = [ROOT / 'docs/data/supplied-four-button-hand-flop-drill.json',
          ROOT / 'docs/data/supplied-four-button-hand-later-bb-flop-drill.json']

def read(path, limit):
    with path.open('rb') as stream:
        raw = stream.read(limit + 1)
    require(len(raw) <= limit, 'Stored collection artifact exceeds cap')
    if path.suffix == '.gz':
        with gzip.open(path, 'rb') as stream:
            expanded = stream.read(limit + 1)
    else:
        expanded = raw
    require(len(expanded) <= limit, 'Expanded collection artifact exceeds cap')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate collection JSON key')
            result[key] = value
        return result
    return raw, json.loads(expanded, object_pairs_hook=unique)

def verify_review(review, drills):
    questions = [dict(collectionHash=PIN, index=i, totalQuestions=2, selection=SELECTION,
                      decision=dict(kind=k, initial=drills[0]['question'] if i == 0 else None,
                                    later=drills[1]['question'] if i == 1 else None)) for i,k in enumerate(KINDS)]
    require(isinstance(review, dict) and set(review) == {'schemaVersion','collectionHash','selection','questions','attempts','counts'}
            and review['schemaVersion'] == SCHEMA and review['collectionHash'] == PIN and review['selection'] == SELECTION
            and review['questions'] == questions, 'Exact typed collection inventory differs')
    for q in review['questions']:
        require(type(q['index']) is int and type(q['totalQuestions']) is int, 'Collection index must be integer')
    attempts = review['attempts']
    require(isinstance(attempts, list) and len(attempts) <= 100, 'Collection attempt cap differs')
    counts = dict(distinctQuestionsAttempted=0,withinLimit=0,outsideLimit=0,strategyDependent=0)
    names = dict(WITHIN_LIMIT='withinLimit',OUTSIDE_LIMIT='outsideLimit',STRATEGY_DEPENDENT='strategyDependent')
    seen = set()
    for index,a in enumerate(attempts):
        require(isinstance(a,dict) and set(a) == {'index','questionIndex','action','feedback'}
                and type(a['index']) is int and a['index'] == index and type(a['questionIndex']) is int
                and a['questionIndex'] in (0,1), 'Collection attempt index differs')
        qi = a['questionIndex']
        matches = [x['feedback'] for x in drills[qi]['attempts'] if x['action'] == a['action']]
        require(len(matches) == 1, 'Illegal action for typed collection question')
        grade = matches[0]
        expected = dict(collectionHash=PIN, questionIndex=qi,
                        decision=dict(kind=KINDS[qi], initial=grade if qi == 0 else None, later=grade if qi == 1 else None))
        require(a['feedback'] == expected, 'Exact typed collection grade differs')
        seen.add(qi)
        counts[names[grade['selected']['classification']]] += 1
    counts['distinctQuestionsAttempted'] = len(seen)
    require(isinstance(review['counts'],dict) and all(type(v) is int for v in review['counts'].values())
            and review['counts'] == counts, 'Repeated-attempt collection counts differ')
    return len(attempts)

def check(manifest_path, review_path):
    manifest_raw, manifest = read(manifest_path,64*1024)
    sources = [dict(kind=k, requestFile=f'supplied-four-button-hand-{s}feedback-request.json',
                    reportFile=f'supplied-four-button-hand-{s}feedback-report.json.gz', reportHash=p)
               for k,s,p in zip(KINDS,['flop-','later-bb-flop-'],PINS,strict=True)]
    require(manifest == dict(schemaVersion='pokerlab-flop-interval-collection-manifest/v1',collectionHash=PIN,sources=sources),
            'Fixed typed manifest identity/order differs')
    root = manifest_path.resolve().parent
    a,b = sources
    receipts = [initial.check(root/a['requestFile'],root/a['reportFile'],DRILLS[0]),
                later.check(root/b['requestFile'],root/b['reportFile'],DRILLS[1])]
    raw, review = read(review_path,1024*1024)
    drills = [json.loads(p.read_bytes()) for p in DRILLS]
    attempts = verify_review(review,drills)
    return dict(schemaVersion='pokerlab-independent-flop-interval-collection/v1',trainerAdmission=False,
                fixedCaseOnly=True,freshLpSolves=0,collectionHash=PIN,sourceQuestionsChecked=2,
                originalEndpointCertificatesChecked=sum(r['originalEndpointCertificatesChecked'] for r in receipts),
                sourceDecisionPolicyChecks=6,attemptsChecked=attempts,sourceReceipts=receipts,
                checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                manifestStoredBytesSha256=hashlib.sha256(manifest_raw).hexdigest(),
                reviewStoredBytesSha256=hashlib.sha256(raw).hexdigest())

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest',type=Path)
    parser.add_argument('review',type=Path)
    parser.add_argument('--output',type=Path)
    args = parser.parse_args()
    if args.output:
        require(args.output.resolve() not in {args.manifest.resolve(),args.review.resolve(),*DRILLS},'Receipt must be distinct')
    text = json.dumps(check(args.manifest,args.review),indent=2)+'\n'
    if args.output:
        with args.output.open('x',encoding='utf-8',newline='\n') as stream:
            stream.write(text)
    print(text,end='')

if __name__ == '__main__':
    main()
