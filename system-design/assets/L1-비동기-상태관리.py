from diagrams import Diagram, Cluster, Edge
from diagrams.aws.network import APIGateway
from diagrams.aws.compute import Fargate
from diagrams.aws.integration import SQS
from diagrams.aws.database import Dynamodb
from diagrams.aws.general import Users
from diagrams import Node
def Box(label, stroke="#9AA0A6", fill="#F5F5F5"):
    return Node(label, shape="box", style="rounded,filled", fillcolor=fill, color=stroke,
                penwidth="1.6", fontname="AppleGothic", fontsize="12", fontcolor="#16191F",
                width="1.9", height="0.85", fixedsize="false", image="")
ga={"fontname":"AppleGothic","bgcolor":"white","fontsize":"20","labelloc":"t","pad":"0.4","nodesep":"0.5","ranksep":"1.0"}
na={"fontname":"AppleGothic","fontsize":"12"}; ea={"fontname":"AppleGothic","fontsize":"11","color":"#5A6B7B"}
BLUE="#147EBA"
with Diagram("L1 — 비동기 잡 + 상태 관리", filename="L1-비동기-상태관리", outformat="png", show=False, direction="LR", graph_attr=ga, node_attr=na, edge_attr=ea):
    c=Users("클라이언트\njobId 폴링")
    nts=Box("국세청 (외부)")
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
        s1=Box("REQUESTED","#86EFAC","#FFFFFF"); s2=Box("COLLECTING","#86EFAC","#FFFFFF"); s3=Box("CALCULATING","#86EFAC","#FFFFFF"); s4=Box("READY (미리보기)","#22C55E","#DCFCE7"); s5=Box("FAILED/재시도","#FDBA74","#FFF7ED")
        s1 >> Edge(color="#22C55E") >> s2 >> Edge(color="#22C55E") >> s3 >> Edge(color="#22C55E") >> s4
        s3 >> Edge(color="#FDBA74", style="dashed") >> s5
