from diagrams import Diagram, Edge
from diagrams.aws.compute import Fargate
from diagrams.aws.general import Users
from diagrams import Node
def Box(label, stroke="#9AA0A6", fill="#F5F5F5"):
    return Node(label, shape="box", style="rounded,filled", fillcolor=fill, color=stroke,
                penwidth="1.6", fontname="AppleGothic", fontsize="12", fontcolor="#16191F",
                width="1.9", height="0.85", fixedsize="false", image="")
ga={"fontname":"AppleGothic","bgcolor":"white","fontsize":"20","labelloc":"t","pad":"0.4","ranksep":"1.0"}
na={"fontname":"AppleGothic","fontsize":"12"}; ea={"fontname":"AppleGothic","fontsize":"11","color":"#5A6B7B"}
with Diagram("L0 — 단순 동기 안 (의도적으로 깨지는 그림)", filename="L0-화이트보드", outformat="png", show=False, direction="LR", graph_attr=ga, node_attr=na, edge_attr=ea):
    c=Users("클라이언트")
    api=Fargate("API 서버 (동기)\n요청 스레드 점유")
    nts=Box("국세청 (외부)\n응답 수십 초~분")
    calc=Fargate("계산 엔진")
    c >> Edge(label="요청") >> api >> Edge(label="수집(느림)", color="#D13212", fontcolor="#D13212") >> nts >> calc
