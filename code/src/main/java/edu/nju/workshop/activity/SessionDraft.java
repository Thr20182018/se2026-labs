package edu.nju.workshop.activity;

/** 场次表单的原始字符串输入，便于校验失败后原样回填. */
public record SessionDraft(
    String startTime, String endTime, String deadline, String location, String capacity) {
}
