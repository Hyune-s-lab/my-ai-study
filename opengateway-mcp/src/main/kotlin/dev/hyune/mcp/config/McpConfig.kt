package dev.hyune.mcp.config

import dev.hyune.mcp.tool.OpenGatewayMcpTools
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpConfig {
    
    @Bean
    fun openGatewayToolProvider(tools: OpenGatewayMcpTools): ToolCallbackProvider {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tools)
            .build()
    }
}
