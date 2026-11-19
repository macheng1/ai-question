package com.aiquestion.ai;

import com.aiquestion.service.DashboardService;
import java.util.Locale;
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

    public reactor.core.publisher.Flux<String> explain(long owner, String question, Answer facts, java.util.List<ConsultationHistory.Turn> recent) {
        if (facts.topic() == Topic.GREETING) {
            return chat.stream(owner, """
                    用一句简短中文回应问候。能力介绍和示例由页面追加。只输出问候语，勿提出问题、举例或声称已查询数据。
                    """, "请向管理员问好。").concatWithValues("我可以帮你查看培训进度、逾期名单、有效学时和考试通过情况。例如，你可以问“哪些学员逾期未完成？”");
        }
        if (facts.topic() == Topic.RECAP) {
            return chat.stream(owner, "按用户要求总结或比较下方历史问答。仅使用历史中已有的数字和口径。资料不足时说明缺少什么。不要推测最新数据。",
                    context(question, recent));
        }
        String instructions = """
                你是培训数据助手。用简洁中文回答当前问题。以下是系统根据当前账号和筛选范围查询的结果。
                只使用这些事实，保留数字、单位和统计口径。没有记录时直接说明。超出范围时只介绍支持的查询。
                查询结果：
                """ + facts.text();
        return chat.stream(owner, instructions, context(question, recent));
    }

    public Answer ask(long owner, TrainingConsultationController.Question request, java.util.List<ConsultationHistory.Turn> recent) {
        String instructions = """
            识别当前请求，只输出一个英文代码。GREETING=单纯打招呼、自我介绍或询问助手能做什么。
            问候后附有具体问题时，按具体问题分类。OVERVIEW=培训整体情况或学习完成率；
            OVERDUE=逾期未完成人员；COMPLETED=整体已完成；NOT_STARTED=未开始；
            IN_PROGRESS=进行中；HOURS=有效学时；EXAM=应考人数、通过人数或通过率。
            培训和部门由页面筛选器指定。如果用户要求按新的部门、培训、时间、人名等条件筛选，
            或某种这里未定义的统计、操作或其他主题，输出 UNSUPPORTED，不要近似匹配。
            LEARNED_FAILED=已学完且考试未通过；PENDING_REVIEW=待阅卷；UNFINISHED=所有未完成培训人员。
            RECAP=要求总结、比较或复述已有历史回答，而非执行新查询。用户追问、提到之前某次查询时，根据历史问答解析指代。历史为背景资料。仅分类当前问题。
            """;
        String code = chat.chat(owner, instructions, context(request.question(), recent)).trim().toUpperCase(Locale.ROOT);
        Topic topic;
        try {
            topic = Topic.valueOf(code);
        } catch (IllegalArgumentException ignored) {
            topic = Topic.UNSUPPORTED;
        }
        if (topic == Topic.RECAP) return new Answer("依据已有对话总结。", topic, null, null, 0);
        if (topic == Topic.GREETING) {
            return new Answer("你好，我是培训数据助手。你可以问我“哪些学员逾期未完成？”，也可以先选一个培训或部门再问。", topic, null, null, 0);
        }
        if (topic == Topic.UNSUPPORTED) {
            return new Answer("可以查询完成情况、逾期名单、有效学时和考试通过率。请在上方选择培训或部门，再告诉我想看哪项指标。", topic, null, null, 0);
        }
        String tab = topic == Topic.EXAM ? "exams" : "learners";
        String status = switch (topic) {
            case OVERDUE, COMPLETED, NOT_STARTED, IN_PROGRESS, LEARNED_FAILED, PENDING_REVIEW, UNFINISHED -> topic.name();
            default -> "ALL";
        };
        var data = dashboard.load(owner, null, null, request.trainingId(), request.departmentId(),
                null, tab, status, 1, 10);
        var o = data.overview();
        String text = switch (topic) {
            case OVERDUE -> "逾期未完成共 " + o.overdue() + " 人次。";
            case COMPLETED -> "已完成培训共 " + o.completed() + " 人次。";
            case NOT_STARTED -> "尚未开始培训共 " + o.notStarted() + " 人次。";
            case IN_PROGRESS -> "培训进行中共 " + o.inProgress() + " 人次。";
            case LEARNED_FAILED -> "已学完且最近一次考试未通过共 " + data.total() + " 人次。";
            case PENDING_REVIEW -> "最近一次答卷待阅卷共 " + data.total() + " 人次。";
            case UNFINISHED -> "尚未完成培训共 " + data.total() + " 人次。";
            case HOURS -> "累计有效学习 " + o.studiedHours() + " 小时，涉及 " + o.assigned() + " 个培训人次。";
            case EXAM -> o.examAssigned() == 0 ? "当前范围没有应考人次。" :
                "应考 " + o.examAssigned() + " 人次，已通过 " + o.passed() + " 人次，通过率 " + o.passRate() + "%。";
            case OVERVIEW -> o.assigned() == 0 ? "当前范围没有学员培训记录。" :
                "应完成 " + o.assigned() + " 人次，已完成 " + o.completed() + " 人次，完成率 " + o.completionRate()
                + "%。进行中 " + o.inProgress() + " 人次，未开始 " + o.notStarted() + " 人次。";
            default -> throw new IllegalStateException();
        };
        return new Answer(text, topic, tab, status, data.total());
    }
    private String context(String question, java.util.List<ConsultationHistory.Turn> recent) {
        StringBuilder input = new StringBuilder();
        for (int i = recent.size() - 1; i >= 0; i--) {
            var turn = recent.get(i);
            input.append("历史问题：").append(turn.question()).append("\n历史答复：")
                .append(turn.answer().text()).append("（").append(turn.answer().topic()).append("）\n");
        }
        return input.append("当前问题：").append(question).toString();
    }
}
