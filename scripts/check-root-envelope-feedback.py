"""Independent complete-menu root policy qualification; no LP optimization or training."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('qualification_root_source',ROOT/'scripts/check-root-security-envelope.py')
source=importlib.util.module_from_spec(spec);sys.modules[spec.name]=source;spec.loader.exec_module(source)
require,close,read=source.require,source.close,source.read
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-root-envelope-feedback-request.json'
REPORT=DATA/'supplied-root-envelope-feedback-report.json.gz'
PUBLIC_SCOPE='ORIGINAL_WHOLE_PUBLIC_ROOT_GAME_NOT_PRIVATE_HAND_CONDITIONED/v1'
EPS=1e-8


def classify(lower,upper,limit):
    return 'WITHIN_LIMIT' if upper<=limit else 'OUTSIDE_LIMIT' if lower>limit else 'STRATEGY_DEPENDENT'


def policy_check(actual,policy,policy_hash,parent,audit,tree,roots,moves):
    require(actual['iterations']==policy['iterations'] and actual['solutionHash']==policy_hash,
            'Owned policy identity differs')
    quality=source.size.parent_check.quality(tree,policy['strategy'])
    source.physical.compare_quality(actual['fullParentQuality'],quality)
    mass=math.fsum(p for p,_ in roots)
    global_br=quality[1][5];upper=audit['globalHeroUpperValue'];margin=upper+EPS-global_br
    included=margin>=-EPS
    require(actual['publicRootQualityScope']==PUBLIC_SCOPE and actual['insideDeclaredFace']==included
            and actual['boundsChecked']==included,'Public root/face scope differs')
    for field,value in [('publicRootReach',1.),('publicRootNashConvBb',quality[3]),
                        ('actualQuestionReach',mass),('counterfactualQuestionReach',mass),
                        ('globalHeroBestResponseBb',global_br),('globalHeroUpperBb',upper),
                        ('requiredSecuritySlackBb',EPS),('inclusionMarginBb',margin)]:
        close(actual[field],value,'Root policy scalar differs: '+field)
    values={move['action']:source.physical.best_response(source.size.Node(-1,edges=tuple(
        (p/mass,dict(node.edges)[move['action']]) for p,node in roots)),policy['strategy'],5) for move in moves}
    losses={action:max(0.,max(values.values())-value) for action,value in values.items()}
    require(set(actual['actionUtilitiesBb'])==set(values) and set(actual['actionLossBb'])==set(losses),
            'Complete move values/losses required')
    for action in values:
        close(actual['actionUtilitiesBb'][action],values[action],'Optimal forced continuation differs')
        close(actual['actionLossBb'][action],losses[action],'Actual decision loss differs')
    regret=math.fsum(policy['strategy'][audit['question']['informationSet']][action]*loss for action,loss in losses.items())
    close(actual['decisionRegretBb'],regret,'Selected root regret differs')
    failures=[]
    if not included:failures.append('REFERENCE_OUTSIDE_DECLARED_FACE')
    if quality[3]>.001:failures+=['FULL_PARENT_GAP','PUBLIC_ROOT_GAP']
    if mass<.0001:failures+=['INSUFFICIENT_ACTUAL_QUESTION_REACH','INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH']
    if regret>.01:failures.append('SELECTED_DECISION_REGRET')
    if included:
        violation=max([0.]+[v for move in moves for v in (
            move['lowerEvBb']-values[move['action']],values[move['action']]-move['upperEvBb'],
            move['lowerLossBb']-losses[move['action']],losses[move['action']]-move['upperLossBb'])])
        close(actual['maximumBoundViolationBb'],violation,'Whole menu bound violation differs')
        if violation>EPS:failures.append('REFERENCE_OUTSIDE_DECISION_BOUNDS')
    else:require(actual['maximumBoundViolationBb'] is None,'Outside-face policy cannot claim checked bounds')
    require(actual['failures']==sorted(failures),'Policy qualification failures differ')
    return sorted(failures)


def check_values(request,report):
    request=copy.deepcopy(request)
    request['envelope']['parent']['input']=source.physical.canonical_input(request['envelope']['parent']['input'])
    request['envelope']['parent']['flop'].sort()
    require(set(request)=={'schemaVersion','envelope','maximumLossBb'}
            and request['schemaVersion']=='pokerlab-root-envelope-feedback-request/v1'
            and math.isfinite(request['maximumLossBb']) and 0<request['maximumLossBb']<=.01,
            'Declared root feedback request differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','envelope','moves',
                         'primary','references','qualifiedForOfflineDrill','rejectionReasons'}
            and report['schemaVersion']=='pokerlab-root-envelope-feedback-report/v1'
            and report['publicationStatus']=='ROOT_ENVELOPE_POLICY_QUALIFICATION_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['request']==request,
            'Root qualification research report identity differs')
    s=report['envelope'];published=read(source.REPORT)
    require(s['request']==request['envelope']==published['request'],'Original declared source request differs')
    if not s['completeOuterBounds']:
        require(s['parent']==published['parent'] and s['context']==published['context']
                and s['envelope'] is None and s['trainerAdmission'] is False and s['rejection'] is not None
                and s['schemaVersion']==published['schemaVersion'] and s['publicationStatus']==published['publicationStatus']
                and s['evScope']==published['evScope'] and s['reachUnits']==published['reachUnits']
                and s['rejection']['kind'] in ('LP','PARENT','ENVELOPE')
                and report['moves']==[] and report['primary'] is None and report['references']==[]
                and report['qualifiedForOfflineDrill'] is False and report['rejectionReasons']==['INCOMPLETE_ROOT_ENVELOPE'],
                'Incomplete numerical source must retain failure and reject qualification')
        parent_receipt=source.size.check_values(s['parent']['request'],s['parent'])
        return dict(schemaVersion='pokerlab-root-feedback-independent-check/v1',trainerAdmission=False,
                    completeOuterBounds=False,retainedSourceFailure=s['rejection'],parentCheck=parent_receipt,
                    qualifiedForOfflineDrill=False,freshLpSolves=0,freshCfrRuns=0)
    require(s==published,'Complete root source was changed or relabelled')
    receipt=source.check_values(request['envelope'],s);a=s['envelope'];parent=s['parent']
    expected=[];limit=request['maximumLossBb']
    for action in a['actions']:
        lo=max(0.,max(y['lowerUtility']-action['upperUtility'] for y in a['actions']))
        hi=max(0.,max(y['upperUtility']-action['lowerUtility'] for y in a['actions']))
        expected.append(dict(action=action['action'],lowerEvBb=action['lowerUtility'],upperEvBb=action['upperUtility'],
                             lowerLossBb=lo,upperLossBb=hi,classification=classify(lo,hi,limit)))
    require(len(report['moves'])==len(expected)==3,'Whole root menu required')
    for actual,wanted in zip(report['moves'],expected,strict=True):
        require(set(actual)==set(wanted) and actual['action']==wanted['action']
                and actual['classification']==wanted['classification'],'Conservative move identity/classification differs')
        for field in ['lowerEvBb','upperEvBb','lowerLossBb','upperLossBb']:
            close(actual[field],wanted[field],'Conservative move bounds differ: '+field)
    tree=source.size.literal_tree(parent,dict(binding=parent['binding']['physicalCarrier']['sourceBinding']),s['request']['parent'])
    roots=[(world['conditionalProbability'],node) for (_,node),world in zip(tree.edges,parent['originalJointPrior'],strict=True)
           if node.key==a['question']['informationSet']]
    require(len(roots)==8 and len(report['references'])==2,'Complete root worlds and both references required')
    policies=[parent['candidate'],*[r['policy'] for r in parent['references']]]
    hashes=[parent['solutionHash'],*[r['policyHash'] for r in parent['references']]]
    checks=[report['primary'],*report['references']]
    failures=[policy_check(c,p,h,parent,a,tree,roots,expected) for c,p,h in zip(checks,policies,hashes,strict=True)]
    reasons=[]
    if not any(m['classification']=='WITHIN_LIMIT' for m in expected):reasons.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
    if not any(m['classification']=='OUTSIDE_LIMIT' for m in expected):reasons.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
    for label,items in zip(['PRIMARY','80000','160000'],failures,strict=True):reasons.extend(label+':'+f for f in items)
    reasons=sorted(set(reasons))
    require(report['rejectionReasons']==reasons and report['qualifiedForOfflineDrill']==(not reasons),
            'Root qualification result differs')
    return dict(schemaVersion='pokerlab-root-feedback-independent-check/v1',trainerAdmission=False,
                completeOuterBounds=True,sourceCheck=receipt,publicRootQualityScope=PUBLIC_SCOPE,
                policiesChecked=3,forcedContinuationValuesChecked=9,decisionLossesChecked=9,
                moveClassifications=[dict(action=m['action'],classification=m['classification']) for m in expected],
                qualifiedForOfflineDrill=not reasons,rejectionReasons=reasons,attainedEndpointClaims=False,
                freshLpSolves=0,freshCfrRuns=0)


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def check():
    result=check_values(read(REQUEST),read(REPORT))
    result.update(checkerSourceSha256=sha(Path(__file__)),requestStoredSha256=sha(REQUEST),reportStoredSha256=sha(REPORT),
                  immutableHelperSha256={p:sha(ROOT/'scripts'/p) for p in ['check-root-security-envelope.py',
                  'check-flop-size-menu.py','check-bb-sizing-physics.py','check-big-blind-flop-discovery.py',
                  'check-supplied-flop-action-bounds.py']})
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as output:output.write(data)
    print(data.decode())
