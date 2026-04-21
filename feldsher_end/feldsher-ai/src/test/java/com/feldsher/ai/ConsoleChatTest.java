package com.feldsher.ai;

import cn.hutool.core.util.StrUtil;
import com.feldsher.ai.entity.ConversationContext;
import com.feldsher.ai.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Scanner;

@SpringBootTest
@ActiveProfiles("test")
public class ConsoleChatTest {

    @Autowired
    private ChatService chatService;

    @Test
    public void testConsoleChat() {
        System.out.println("========================================");
        System.out.println("       医疗AI助手 - 控制台测试模式");
        System.out.println("========================================");
        System.out.println();
        System.out.println("使用说明：");
        System.out.println("  - 输入您的症状或问题开始对话");
        System.out.println("  - 输入 '/new' 开始一个新的对话");
        System.out.println("  - 输入 '/exit' 结束当前会话");
        System.out.println("  - 输入 '/status' 查看当前对话状态");
        System.out.println();
        
        Scanner scanner = new Scanner(System.in);
        String currentConversationId = null;
        
        while (true) {
            try {
                System.out.print("\n【您】> ");
                String input = scanner.nextLine().trim();
                
                if (input.isEmpty()) {
                    continue;
                }
                
                if ("/exit".equalsIgnoreCase(input)) {
                    System.out.println("\n【助手】感谢使用医疗AI助手！祝您身体健康！");
                    if (currentConversationId != null) {
                        chatService.endConversation(currentConversationId);
                    }
                    break;
                }
                
                if ("/new".equalsIgnoreCase(input)) {
                    if (currentConversationId != null) {
                        chatService.clearContext(currentConversationId);
                    }
                    ConversationContext newContext = chatService.createConversation();
                    currentConversationId = newContext.getConversationId();
                    System.out.println("\n【助手】已创建新对话，对话ID: " + currentConversationId);
                    System.out.println("【助手】您好！我是您的医疗助手，请问您有什么健康问题需要咨询吗？");
                    continue;
                }
                
                if ("/status".equalsIgnoreCase(input)) {
                    if (currentConversationId == null) {
                        System.out.println("\n【系统】当前没有活动的对话，请先开始一个对话。");
                    } else {
                        ConversationContext context = chatService.getContext(currentConversationId);
                        if (context != null) {
                            System.out.println("\n【系统】对话状态：");
                            System.out.println("  - 对话ID: " + context.getConversationId());
                            System.out.println("  - 当前阶段: " + (context.getCurrentPhase() != null ? context.getCurrentPhase().getDescription() : "未知"));
                            System.out.println("  - 已问问题数: " + context.getQuestionCount());
                            System.out.println("  - 当前意图: " + (context.getCurrentIntent() != null ? context.getCurrentIntent().getDescription() : "未识别"));
                            System.out.println("  - 当前Agent: " + (context.getCurrentAgent() != null ? context.getCurrentAgent().getName() : "无"));
                            System.out.println("  - 当前Skill: " + (context.getCurrentSkill() != null ? context.getCurrentSkill().getName() : "无"));
                            System.out.println("  - 消息总数: " + (context.getMessages() != null ? context.getMessages().size() : 0));
                            System.out.println("  - 活动状态: " + (context.isActive() ? "活动中" : "已结束"));
                        } else {
                            System.out.println("\n【系统】对话不存在或已被清除。");
                        }
                    }
                    continue;
                }
                
                if (currentConversationId == null) {
                    ConversationContext context = chatService.createConversation();
                    currentConversationId = context.getConversationId();
                    System.out.println("\n【系统】创建新对话，对话ID: " + currentConversationId);
                }
                
                System.out.println("\n【助手】正在思考...");
                
                long startTime = System.currentTimeMillis();
                ChatService.ChatResult result = chatService.processMessage(currentConversationId, input);
                long endTime = System.currentTimeMillis();
                
                System.out.println("\n【助手】");
                System.out.println(result.getResponse());
                
                System.out.println("\n【系统信息】");
                System.out.println("  - 处理耗时: " + (endTime - startTime) + "ms");
                System.out.println("  - 对话状态: " + (result.isComplete() ? "已完成" : "进行中"));
                if (result.getPhase() != null) {
                    System.out.println("  - 当前阶段: " + result.getPhase());
                }
                if (result.getProgress() != null) {
                    System.out.println("  - 问诊进度: " + (int)(result.getProgress() * 100) + "%");
                }
                if (result.isEmergency()) {
                    System.out.println("  - ⚠️ 紧急情况提示");
                }
                
                if (result.isComplete()) {
                    System.out.println("\n【助手】对话已完成。您可以输入 '/new' 开始新的对话，或输入 '/exit' 退出。");
                }
                
            } catch (Exception e) {
                System.out.println("\n【错误】发生异常: " + e.getMessage());
                e.printStackTrace();
                System.out.println("\n您可以继续对话或输入 '/new' 重新开始。");
            }
        }
        
        scanner.close();
        System.out.println("\n========================================");
        System.out.println("            会话已结束");
        System.out.println("========================================");
    }
}
