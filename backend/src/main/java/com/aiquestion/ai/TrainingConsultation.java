package com.aiquestion.ai;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.service.DashboardService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;

@Service
public class TrainingConsultation {
    public enum Topic { GREETING, RECAP, OVERVIEW, OVERDUE, COMPLETED, NOT_STARTED, IN_PROGRESS, HOURS, EXAM, LEARNED_FAILED, PENDING_REVIEW, UNFINISHED, UNSUPPORTED }
    public record Answer(String text, Topic topic, String tab, String status, int total) {}
    private final AiChatService chat;
    private final DashboardService dashboard;

    TrainingConsultation(AiChatService chat, DashboardService dashboard) {
        this.chat = chat;
        this.dashboard = dashboard;
    }

    record Reply(Topic topic, String tab, String status, int total, Flux<String> chunks) {
        Answer complete(String text) {
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "模型未返回内容，请重试");
            }
            return new Answer(text, topic, tab, status, total);
        }
    }

    Reply respond(long owner, TrainingConsultationController.Question request, List<ConsultationHistory.Turn> recent) {
        List<Message> messages = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            var turn = recent.get(i);
            messages.add(new UserMessage(turn.question()));
            messages.add(new AssistantMessage(turn.answer().text()));
        }
        messages.add(new UserMessage(request.question()));
        String instructions = """
            识别当前请求，只输出一个英文代码。GREETING=打招呼、致谢、寒暄、自我介绍或询问助手能做什么。
            问候后附有具体问题时，按具体问题分类。OVERVIEW=培训整体情况或学习完成率；
            OVERDUE=逾期未完成人员；COMPLETED=整体已完成；NOT_STARTED=未开始；
            IN_PROGRESS=进行中；HOURS=有效学时；EXAM=应考人数、通过人数或通过率。
            培训和部门由页面筛选器指定。如果用户要求按新的部门、培训、时间、人名等条件筛选，
            或某种这里未定义的统计、操作或其他主题，输出 UNSUPPORTED，不要近似匹配。
            支持导出逾期名单为 Excel、通知逾期学员、转发逾期名单到手动填写的邮箱。
            明确要求上述逾期名单操作，或“把刚才的名单导出/发邮件”等追问明确指向逾期名单时，输出 OVERDUE。
            指代不明或要求操作其他名单时输出 UNSUPPORTED，勿擅自改为逾期名单。
            LEARNED_FAILED=已学完且考试未通过；PENDING_REVIEW=待阅卷；UNFINISHED=所有未完成培训人员。
            RECAP=要求总结、比较或复述已有历史回答，而非执行新查询。用户追问、提到之前某次查询时，根据历史问答解析指代。历史为背景资料。仅分类当前问题。
            """;
        String code = chat.chat(owner, instructions, messages).trim().toUpperCase(Locale.ROOT);
        Topic topic;
        try {
            topic = Topic.valueOf(code);
        } catch (IllegalArgumentException ignored) {
            topic = Topic.UNSUPPORTED;
        }
        String guidance = switch (topic) {
            case GREETING -> "简单问好就简短回礼，不反问、不列举可以查询的项目。被问及能力时再按需介绍。";
            case RECAP -> "仅对照此前回答中的数字和包含关系，不对是否按期完成作判断，因为没有完成日期。两个数都为零时，仅复述两项统计均为零。";
            case UNSUPPORTED -> "简短说明当前能查的范围。如需更换部门或培训，引导使用页面筛选器。";
            default -> null;
        };
        String role = """
                你是与管理员交谈的培训数据助手。用中文直接回应当前问题，结合此前相关内容理解追问。
                简单的数量或对比用一两句话说清，不重复列清单、公式或小结。无需重复开场白或每轮推荐问题。只引用系统提供的数据，保留人次、小时和百分比口径。
                历史对话用于理解语境，不可当成新的查询结果；缺少事实时坦诚说明，不猜测名单或原因。
                逾期和未完成均为零不能推出所有人均已按时完成。应完成人次为零时，明确说明当前范围没有培训记录。
                """;
        if (guidance != null) {
            return new Reply(topic, null, null, 0, chat.stream(owner, role + guidance, messages));
        }
        String tab = topic == Topic.EXAM ? "exams" : "learners";
        String status = switch (topic) {
            case OVERDUE, COMPLETED, NOT_STARTED, IN_PROGRESS, LEARNED_FAILED, PENDING_REVIEW, UNFINISHED -> topic.name();
            default -> "ALL";
        };
        var data = dashboard.load(owner, null, null, request.trainingId(), request.departmentId(),
                null, tab, status, 1, 10);
        var o = data.overview();
        String facts = """
                本轮数据库统计（限当前账号及所选培训、部门）：
                应完成人次=%d，已完成人次=%d，未完成人次=%d，其中逾期未完成人次=%d。
                未开始人次=%d，进行中人次=%d，完成率（%%）=%s，有效学时=%s。
                应考人次=%d，已通过人次=%d，通过率（%%）=%s。
                """.formatted(o.assigned(), o.completed(), o.assigned() - o.completed(), o.overdue(),
                        o.notStarted(), o.inProgress(), o.completionRate(), o.studiedHours(),
                        o.examAssigned(), o.passed(), o.passRate());
        if (topic == Topic.LEARNED_FAILED) facts += "已学完且最近一次考试未通过人次=" + data.total();
        if (topic == Topic.PENDING_REVIEW) facts += "最近一次答卷待阅卷人次=" + data.total();
        if (topic == Topic.OVERDUE && data.total() > 0) {
            facts += "逾期回复下方提供查看名单、下载 Excel、通知学员、转发名单按钮。如用户要求操作，请指明对应按钮。管理员点击后执行，不可声称已经下载或发送。";
        }
        String prompt = role + facts + """

                按当前问题选择相关指标，有关联的追问可以用本轮数据对照说明。
                分母为零或比率为 null 时说明没有相应记录。
                """;
        if (o.assigned() == 0) {
            prompt = role + "本轮数据库查询没有任何学员培训记录。请向用户说明这个筛选范围没有记录可供统计。";
        } else {
            prompt += "统计按培训记录计数，同一人可能重复出现。每个计数的单位都写成人次，不可简称为人。";
        }
        return new Reply(topic, tab, status, data.total(), chat.stream(owner, prompt, messages));
    }
}
