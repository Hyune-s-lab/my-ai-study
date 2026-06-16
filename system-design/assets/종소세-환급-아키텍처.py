from diagrams import Diagram, Cluster, Edge
from diagrams.aws.network import APIGateway
from diagrams.aws.compute import Fargate
from diagrams.aws.integration import SQS, SimpleNotificationServiceSns, StepFunctions
from diagrams.aws.database import Dynamodb, ElastiCache
from diagrams.aws.storage import SimpleStorageServiceS3
from diagrams.aws.general import Users
from diagrams.generic.blank import Blank

ga={"fontname":"AppleGothic","bgcolor":"white","fontsize":"22","labelloc":"t","pad":"0.5","nodesep":"0.6","ranksep":"1.0"}
na={"fontname":"AppleGothic","fontsize":"12"}
ea={"fontname":"AppleGothic","fontsize":"11","color":"#5A6B7B"}
RED="#D13212"; BLUE="#147EBA"

with Diagram("종합소득세 환급 / 연말정산 — 서버 아키텍처",
             filename="종소세-환급-아키텍처", outformat="png", show=False,
             direction="LR", graph_attr=ga, node_attr=na, edge_attr=ea):
    client=Users("클라이언트\n(모바일 앱)")
    nts=Blank("국세청 홈택스/손택스\n(외부 · 느림)")
    with Cluster("AWS Cloud", graph_attr={"fontname":"AppleGothic","bgcolor":"white","pencolor":"#232F3E"}):
        apigw=APIGateway("API Gateway\n인증·라우팅")
        api=Fargate("API (ECS)\n잡 생성·jobId")
        sqs=SQS("SQS\n잡 큐+DLQ")
        worker=Fargate("Worker (ECS)\n수집·Retry·CB")
        calc=Fargate("계산 엔진\n세법 룰")
        ddb=Dynamodb("DynamoDB\n상태·멱등키")
        cache=ElastiCache("ElastiCache\n수집 캐시")
        s3=SimpleStorageServiceS3("S3\n원천·신고서")
        sns=SimpleNotificationServiceSns("SNS/Pinpoint\n완료 푸시")
        sfn=StepFunctions("Step Functions\n셀프 신고 사가")

    client >> Edge(label="요청·폴링") >> apigw >> api >> Edge(label="enqueue") >> sqs >> Edge(label="consume") >> worker
    worker >> Edge(label="계산") >> calc
    worker >> Edge(label="수집", color=RED, fontcolor=RED) >> nts
    worker >> Edge(label="상태/결과 저장") >> ddb
    worker >> Edge(label="캐시") >> cache
    worker >> s3
    api >> Edge(label="상태 조회", style="dashed", color=BLUE, fontcolor=BLUE) >> ddb
    apigw >> Edge(label="제출 요청") >> sfn
    sfn >> Edge(label="제출(멱등키)", color=RED, fontcolor=RED) >> nts
    sfn >> Edge(label="제출상태") >> ddb
    sns >> Edge(label="완료 푸시", style="dashed") >> client
