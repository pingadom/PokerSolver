"""Independent new-board whole-batch qualification and canonical cached review; no optimizer."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1];DATA=ROOT/'docs/data'
def load(name,file):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file);m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m);return m
coverage=load('unpaired_source_discovery','check-unpaired-flop-discovery.py')
legacy=load('unpaired_source_assessments','check-shared-call-flop-feedback.py')
physical=coverage.physical
require,close,read=coverage.require,coverage.close,coverage.read
policy_check=legacy.policy_check
REQUEST=DATA/'supplied-unpaired-shared-flop-request.json'
REPORT=DATA/'supplied-unpaired-shared-flop-report.json.gz'
REVIEW=DATA/'supplied-unpaired-shared-flop-review.json'
BUDGET=DATA/'unpaired-shared-flop-generation-budget.json'
HANDS=['2c 3c','Ah Kh','As Qs','Qh Th']
SHARING='ONE_GAME_TWO_FRESH_POLICIES_ALL_QUESTIONS_CHECKED/v1'
REPORT_HASH='c5845cabc3a93208520549d5893ea311ad3c5409f792b96729b0d325fab077c9'
REFERENCE_PINS=['a28f2afb464567847d2f68eb39f16adf3d129227d6eb57fd654d8bde84655e1a', '475ee32e565ac6339c316d93381266eac98527a6d616ffd2b7cb2b60dc201400']
REPORT_SHA='39207f874e76252e592fde3645e7b10d8447876184c7216e8298295ba944d0a9'
REVIEW_SHA='dad8411f76b71d9291a8a6fce5ff1ad27205ae42523c6f9ab81918f6c084bf58'

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def check(request_path=REQUEST,report_path=REPORT,*,review_path=REVIEW,bind_saved_bytes=True):
    request,request_raw,_=read(request_path);report,raw,expanded=read(report_path);budget,_,_=read(BUDGET)
    control,control_raw,_=read(coverage.REPORT);discovery_receipt,receipt_raw,_=read(DATA/'supplied-unpaired-flop-discovery-independent-check.json')
    require(digest(request_path)==budget['requestSha256'] and digest(DATA/'supplied-unpaired-flop-discovery-independent-check.json')==budget['discoveryReceiptSha256']
        and hashlib.sha256(control_raw).hexdigest()==budget['discoveryReportStoredSha256'],'Predeclared source or diagnostic control differs')
    require(digest(ROOT/'solver/src/main/java/com/pokerlab/solver/SixMaxRefinedFlopFeedback.java')==budget['unchangedGateSourceSha256'],'Original qualification gate changed')
    require(discovery_receipt['solvedCases']==4 and discovery_receipt['originalActionEndpointCertificatesChecked']==32,'Positive discovery evidence differs')
    require(budget['physicalGameBuilds']==1 and budget['conditionalStudies']==4 and budget['maximumActionComputations']==8 and budget['maximumParentSolves']==12
        and budget['freshReferenceRuns']==2 and budget['referenceIterations']==[80000,160000] and budget['totalReferenceIterations']==240000
        and budget['selectedPolicyAssessments']==12 and budget['automaticRetries']==0 and budget['allOriginalNumericAndCompilerCapsPreserved'] is True,'Generation/reference reservation differs')
    declared=copy.deepcopy(request);declared['parent']['connected']=coverage.connected(request['parent']['connected'])
    require(set(request)=={'schemaVersion','parent','intervalAlgorithm','heroHands','publicPreflopHistory','referenceBudget','maximumLossBb'}
        and request['schemaVersion']=='pokerlab-shared-flop-feedback-request/v1' and declared['parent']==control['request']['parent']
        and request['heroHands']==HANDS and request['publicPreflopHistory']=='|BB:call' and request['maximumLossBb']==.01
        and request['intervalAlgorithm']==legacy.coverage.INTERVAL_ALGORITHM
        and request['referenceBudget']==dict(algorithm='FRESH_CFR_PLUS_LITERAL_INACTIVE_PASSES_80K_160K/v1',iterations=[80000,160000],totalIterations=240000),'Whole supplied game and ordered hands differ')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','referenceSharing','request','reservation','references','cases','qualifiedForOfflineDrill','rejectionReasons'}
        and report['schemaVersion']=='pokerlab-shared-flop-feedback-report/v1' and report['publicationStatus']=='SHARED_REFERENCE_PHYSICAL_FLOP_RESEARCH_ONLY'
        and report['trainerAdmission'] is False and report['referenceSharing']==SHARING and report['request']==declared,'Source protocol differs')
    require(report['reservation']==dict(physicalGameBuilds=1,conditionalStudies=4,maximumParentSolves=12,maximumActionComputations=8,
        freshReferenceRuns=2,totalReferenceIterations=240000,selectedPolicyAssessments=12,automaticRetries=0)
        and len(report['cases'])==4 and len(report['references'])==2,'Missing hand/reference or changed work reservation')
    parent=report['cases'][0]['conditional']['parent'];root,parent_receipt=coverage.checked_parent(parent,declared['parent'])
    require(parent==read(coverage.PARENT_REPORT)[0],'Original declared physical parent differs')
    selected={(i,'|BB:call'):dict(node.edges)['call'].edges[0][1] for i,(_,node) in enumerate(root.edges)}
    context=dict(root=root,prior=parent['originalJointPrior'],selected=selected)
    def sizes(node):
        rows=[sizes(c) for _,c in node.edges];return 1+sum(n for n,_ in rows),int(node.actor==-2)+sum(t for _,t in rows)
    def constant(node,actor):
        require(node.actor!=actor,'Supposed inactive player acts')
        if node.actor==-2:return node.utilities[actor]
        values=[constant(c,actor) for _,c in node.edges];require(values and all(v==values[0] for v in values),'Inactive payoff is not a literal constant');return values[0]
    nodes,terminals=sizes(root);require((nodes,terminals)==(1321,720),'Complete physical reference inventory differs')
    constants={str(a):constant(root,a) for a in [0,1,2,4]};visited=0
    for reference,iteration in zip(report['references'],[80000,160000],strict=True):
        require(set(reference)=={'policy','work'} and reference['policy']['iterations']==iteration,'Fresh reference identity differs')
        expected=dict(algorithm='FRESH_CFR_PLUS_LITERAL_INACTIVE_PASSES_80K_160K/v1',validatedTreeNodes=nodes,inactiveUtilities=constants,
            statistics=dict(visitedNodes=(2*nodes+4)*iteration,terminalNodes=2*terminals*iteration,sampledChanceNodes=0,baselineCorrections=0),prunedSuffixRoots=4*iteration)
        require(reference['work']==expected,'Inactive pruning or explicit fresh traversal counts differ');visited+=expected['statistics']['visitedNodes']
    certificates=compiler=pivots=work=0;failures=[];classes=[]
    for i,(hand,item) in enumerate(zip(HANDS,report['cases'],strict=True)):
        require(set(item)=={'index','conditional','moves','primary','references','rejectionReasons'} and item['index']==i,'Whole-batch question ordering differs')
        positive=copy.deepcopy(control['cases'][2*i+1]['conditional'])
        positive['schemaVersion']='pokerlab-refined-flop-conditional-report/v3';positive['request']['intervalAlgorithm']=request['intervalAlgorithm']
        for action in positive['actions']:action['diagnostic']['algorithm']=request['intervalAlgorithm']
        require(item['conditional']==positive,'Hand-specific original endpoints changed')
        certificates+=coverage.check_solved(control['cases'][2*i+1]['request'],control['cases'][2*i+1]['conditional'],parent,root)
        require(item['moves']==control['cases'][2*i+1]['moves'],'Move classifications differ')
        for action in positive['actions']:
            a=action['diagnostic']['work'];compiler+=a['compilerUnits'];pivots+=a['intervalLpPivots'];work+=a['intervalLpArithmeticWork']
        roots=[]
        for j,world in enumerate(parent['originalJointPrior']):
            if world['dealtCombos'][3]==hand:
                initial=selected[j,'|BB:call'];roots.append((j,world['conditionalProbability'],initial,dict(initial.edges)['bet']))
        require(len(roots)==6 and len(item['references'])==2,'Private world/policy-assessment coverage differs')
        key=f'3:supplied-flop-one-bet:{hand}:|BB:call:{coverage.BOARD}:bet'
        rejected=['PRIMARY:'+f for f in policy_check(item['primary'],parent['candidate'],parent['solutionHash'],context,positive['actions'],roots,key)]
        for check,reference,pin in zip(item['references'],report['references'],REFERENCE_PINS,strict=True):
            rejected += [str(reference['policy']['iterations'])+':'+f for f in policy_check(check,reference['policy'],pin,context,positive['actions'],roots,key)]
        classification={m['classification'] for m in item['moves']};classes.append([m['classification'] for m in item['moves']])
        if 'WITHIN_LIMIT' not in classification:rejected.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
        if 'OUTSIDE_LIMIT' not in classification:rejected.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
        require(item['rejectionReasons']==sorted(set(rejected)),'Hand-specific failures concealed');failures += [str(i)+':'+f for f in rejected]
    require(certificates==budget['maximumEndpointCertificates']==32 and visited<=budget['maximumReferenceVisitedNodes']==635040000
        and compiler<=budget['maximumCompilerUnits']==160000000 and pivots<=budget['maximumLpPivots']==320000
        and work<=budget['maximumLpArithmeticWork']==128000000000 and len(expanded)<=budget['maximumReportExpandedBytes']==8388608,'Original work caps/reservations exceeded')
    require(report['rejectionReasons']==sorted(set(failures)) and report['qualifiedForOfflineDrill'] is (not failures),'Whole-batch admission differs')
    if bind_saved_bytes:require(hashlib.sha256(raw).hexdigest()==REPORT_SHA,'Frozen source report bytes differ')
    receipt=dict(schemaVersion='pokerlab-independent-unpaired-shared-flop-check/v1',trainerAdmission=False,qualifiedForOfflineDrill=not failures,
        reportHash=REPORT_HASH,questionsChecked=4,endpointCertificatesChecked=32,selectedPolicyAssessments=12,sharedReferencePolicies=2,
        declaredFreshReferenceIterations=240000,referenceVisitedNodes=visited,freshLpSolves=0,freshCfrRuns=0,parentCheck=parent_receipt,
        compilerUnits=compiler,intervalLpPivots=pivots,intervalLpArithmeticWork=work,classifications=classes,
        checkerSourceSha256=digest(Path(__file__)),generationBudgetSha256=digest(BUDGET),sourceReportStoredSha256=hashlib.sha256(raw).hexdigest())
    if review_path is not None:receipt['reviewCheck']=check_review(report,review_path,bind_saved_bytes=bind_saved_bytes)
    return receipt

def check_review(report, review_path=REVIEW, *, bind_saved_bytes=True):
    review,raw,expanded=read(review_path)
    require(len(raw)<=512*1024 and len(expanded)<=512*1024, 'Review exceeds original byte cap')
    require(set(review)=={'schemaVersion','reportHash','attempts','counts'}
            and review['schemaVersion']=='pokerlab-shared-flop-review/v1' and review['reportHash']==REPORT_HASH
            and type(review['attempts']) is list and len(review['attempts'])<=100, 'Review protocol or attempt bound differs')
    template=read(DATA/'supplied-weak-hand-qualified-flop-drill.json')[0]['question']
    status='SHARED_REFERENCE_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY'
    expected=[];seen=set();counts={'WITHIN_LIMIT':0,'OUTSIDE_LIMIT':0,'STRATEGY_DEPENDENT':0}
    for index,attempt in enumerate(review['attempts']):
        require(set(attempt)=={'index','question','action','feedback'} and attempt['index']==index, 'Review attempt fields or ordering differ')
        require(set(attempt['question'])=={'index','totalQuestions','decision'}, 'Review question fields differ')
        question_index=attempt['question']['index']
        require(type(question_index) is int and 0<=question_index<len(report['cases']), 'Review question index differs')
        case=report['cases'][question_index];conditional=case['conditional']
        decision=dict(template,reportHash=REPORT_HASH,publicationStatus=status,
            selection='ORDERED_ALL_QUALIFIED_SHARED_REFERENCE_QUESTIONS/v1',
            informationSet=conditional['informationSet'],heroCards=conditional['request']['heroCards'],context=conditional['context'])
        question=dict(index=question_index,totalQuestions=len(report['cases']),decision=decision)
        menu={m['action']:m for m in case['moves']}
        require(attempt['action'] in menu, 'Review action is not legal')
        move=menu[attempt['action']]
        feedback=dict(reportHash=REPORT_HASH,publicationStatus=status,trainerAdmission=False,
            evScope=conditional['evScope'],maximumLossBb=.01,selected=move,alternatives=case['moves'])
        expected.append(dict(index=index,question=question,action=attempt['action'],feedback=feedback))
        seen.add(question_index);counts[move['classification']]+=1
    expected_counts=dict(withinLimit=counts['WITHIN_LIMIT'],outsideLimit=counts['OUTSIDE_LIMIT'],
        strategyDependent=counts['STRATEGY_DEPENDENT'],distinctQuestions=len(seen),totalQuestions=len(report['cases']))
    require(review['attempts']==expected and review['counts']==expected_counts, 'Canonical shared review grades, questions or coverage differ')
    if bind_saved_bytes:require(hashlib.sha256(raw).hexdigest()==REVIEW_SHA, 'Fixed review bytes differ')
    return dict(attemptsChecked=len(expected),counts=expected_counts,sourceReviewSha256=hashlib.sha256(raw).hexdigest())

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    require(not args.output.exists(),'Output receipt must be new');result=check()
    with args.output.open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print(json.dumps(result,indent=2))
