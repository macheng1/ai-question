ALTER TABLE admin_ai_settings
    ADD provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'bailian',
    ADD base_url VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'https://dashscope.aliyuncs.com/compatible-mode/v1';
