package com.aiquestion.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 一道待导入的题目，位置用于回报 Excel 行号或 Word 题号。 */
final class ImportQuestion {
    final String location;
    final String type;
    String stem;
    String answer = "";
    String analysis = "";
    String difficulty = "";
    String error;
    final Map<String, String> options = new LinkedHashMap<>();

    ImportQuestion(String location, String type, String stem) {
        this.location = location;
        this.type = type;
        this.stem = stem;
    }
}
