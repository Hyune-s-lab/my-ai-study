from diagrams import Diagram, Cluster, Edge
from diagrams.programming.framework import Spring
from diagrams.onprem.queue import Kafka
from diagrams.onprem.inmemory import Redis
from diagrams.elastic.elasticsearch import Elasticsearch, Kibana, Logstash
from diagrams.onprem.monitoring import Grafana, Prometheus
from diagrams.onprem.database import Postgresql
from diagrams.onprem.client import Users
from diagrams import Node
def Box(label, stroke="#9AA0A6", fill="#F5F5F5"):
    return Node(label, shape="box", style="rounded,filled", fillcolor=fill, color=stroke,
                penwidth="1.6", fontname="AppleGothic", fontsize="12", fontcolor="#16191F",
                width="1.9", height="0.85", fixedsize="false", image="")
ga={"fontname":"AppleGothic","bgcolor":"white","fontsize":"22","labelloc":"t","pad":"0.5","nodesep":"0.55","ranksep":"1.1"}
na={"fontname":"AppleGothic","fontsize":"12"}; ea={"fontname":"AppleGothic","fontsize":"11","color":"#5A6B7B"}
RED="#D13212"; GREEN="#3F8624"; BLK="#231F20"
with Diagram("스프링 클라우드 마이크로서비스 레퍼런스 아키텍처", filename="스프링-마이크로서비스-레퍼런스", outformat="png", show=False, direction="LR", graph_attr=ga, node_attr=na, edge_attr=ea):
    client=Users("클라이언트\n웹/모바일")
    keycloak=Box("키클록\nKeycloak (인증·토큰)", "#C4302B", "#FDECEC")
    config=Spring("구성 저장소\nSpring Cloud Config")
    eureka=Spring("서비스 디스커버리\nEureka")
    gw=Spring("서비스 게이트웨이\nSpring Cloud Gateway")
    org=Spring("조직 서비스\n+Resilience4j")
    lic=Spring("라이선싱 서비스\n+Resilience4j")
    kafka=Kafka("Kafka\n토픽(이벤트)")
    orgdb=Postgresql("조직 DB")
    licdb=Postgresql("라이선싱 DB")
    redis=Redis("Redis\n조회 캐시")
    with Cluster("도커 — 관측(Observability)", graph_attr={"fontname":"AppleGothic","bgcolor":"white","pencolor":"#0EA5E9","style":"dashed"}):
        zipkin=Box("Zipkin\n분산 추적", "#FF6600", "#FFF4E5")
        logstash=Logstash("Logstash"); es=Elasticsearch("Elasticsearch"); kibana=Kibana("Kibana")
        prom=Prometheus("Prometheus"); grafana=Grafana("Grafana")
    client >> Edge(label="토큰") >> gw
    client >> Edge(label="인증·토큰") >> keycloak
    gw >> Edge(label="등록/조회", color=GREEN) >> eureka
    gw >> org
    gw >> lic
    org >> Edge(label="구성 로드", color=GREEN, style="dashed") >> config
    org >> Edge(label="조회") >> orgdb
    lic >> licdb
    lic >> Edge(label="캐시", color=RED, fontcolor=RED) >> redis
    org >> Edge(label="발행", color=BLK, fontcolor=BLK) >> kafka >> Edge(label="구독", color=BLK, fontcolor=BLK) >> lic
    org >> Edge(label="추적") >> zipkin
    org >> Edge(label="로그") >> logstash >> es
    kibana >> Edge(label="질의") >> es
    lic >> Edge(label="지표") >> prom
    grafana >> Edge(label="질의") >> prom
