package dev.hyune.mcp.document

/**
 * 문서의 청크를 나타내는 데이터 클래스
 * 마크다운 헤더 기반으로 분할된 각 섹션을 표현
 */
data class DocumentChunk(
    /** 청크의 고유 식별자 (예: "authentication", "chat-completions-api") */
    val id: String,
    
    /** 섹션 제목 (예: "## 인증", "### 요청 파라미터") */
    val title: String,
    
    /** 헤더 레벨 (1 = #, 2 = ##, 3 = ###) */
    val level: Int,
    
    /** 섹션의 전체 내용 (헤더 포함) */
    val content: String,
    
    /** 부모 섹션들의 경로 (예: ["OpenGateway API 문서", "인증"]) */
    val breadcrumb: List<String> = emptyList(),
    
    /** 원본 문서 파일명 */
    val sourceFile: String = ""
)

/**
 * 문서 목차 아이템
 */
data class OutlineItem(
    val id: String,
    val title: String,
    val level: Int,
    val children: MutableList<OutlineItem> = mutableListOf()
)

/**
 * 문서 전체 목차
 */
data class DocumentOutline(
    val sourceFile: String,
    val items: List<OutlineItem>
)
