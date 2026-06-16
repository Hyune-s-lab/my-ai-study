from diagrams import Diagram, Cluster, Edge
from diagrams.aws.network import APIGateway
from diagrams.aws.compute import Fargate
from diagrams.aws.integration import SQS
from diagrams.aws.database import Dynamodb
from diagrams.aws.general import Users
from diagrams.generic.blank import Blank
ga={"fontname":"AppleGothic","bgcolor":"white","fontsize":"20","labelloc":"t","pad":"0.4","nodesep":"0.5","ranksep":"1.0"}
na={"fontname":"AppleGothic","fontsize":"12"}; ea={"fontname":"AppleGothic","fontsize":"11","color":"#5A6B7B"}
BLUE="#147EBA"
with Diagram("L1 — 비동기 잡 + 상태 관리", filename="L1-비동기-상태관리", outformat="png", show=False, direction="LR", graph_attr=ga, node_attr=na, edge_attr=ea):
    c=Users("클라이언트\njobId 폴링")
    nts=Blank("국세청 (외부)")
    apigw=APIGateway("API Gateway")
    api=Fargate("API (ECS)\n잡 생성·jobId")
    sqs=SQS("SQS\n잡 큐")
    worker=Fargate("Worker (ECS)\n수집→계산")
    calc=Fargate("계산 엔진")
    ddb=Dynamodb("DynamoDB\n잡 상태·결과")
    c >> Edge(label="요청·폴링") >> apigw >> api >> Edge(label="enqueue") >> sqs >> Edge(label="consume") >> worker
    worker >> Edge(label="수집", color="#D13212", fontcolor="#D13212") >> nts
    worker >> Edge(label="계산") >> calc
    worker >> Edge(label="상태/결과") >> ddb
    api >> Edge(label="상태 조회", style="dashed", color=BLUE, fontcolor=BLUE) >> ddb
    with Cluster("상태 머신 (jobId, DynamoDB)", graph_attr={"fontname":"AppleGothic","bgcolor":"white","pencolor":"#22C55E"}):
        s1=Blank("REQUESTED"); s2=Blank("COLLECTING"); s3=Blank("CALCULATING"); s4=Blank("READY"); s5=Blank("FAILED/재시도")
        s1 >> Edge(color="#22C55E") >> s2 >> Edge(color="#22C55E") >> s3 >> Edge(color="#22C55E") >> s4
        s3 >> Edge(color="#FDBA74", style="dashed") >> s5
