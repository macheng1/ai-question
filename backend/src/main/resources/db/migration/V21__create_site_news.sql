CREATE TABLE site_news (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  summary VARCHAR(500),
  content TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  published_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL
);

CREATE INDEX idx_site_news_status_published ON site_news(status, published_at);

INSERT INTO site_news (title, summary, content, status, published_at, created_at, updated_at) VALUES
('智企云考平台使用公告', '平台考试、题库和在线自测功能已上线，欢迎体验。', '智企云考已开放企业考试、公开题库和在线自测功能。使用过程中如遇到问题，可通过帮助手册了解操作流程。', 'PUBLISHED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
('题库与考试功能更新说明', '题库分类、随机试卷和考试统计功能持续优化。', '我们会持续完善题库内容与考试统计能力，为企业培训和个人学习提供更稳定的使用体验。', 'PUBLISHED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
