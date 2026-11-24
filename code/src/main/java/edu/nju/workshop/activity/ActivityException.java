package edu.nju.workshop.activity;

/** 携带 HTTP 状态码及页面提示的活动业务拒绝结果. */
public class ActivityException extends RuntimeException {

  private final int status;

  /** 保存业务拒绝对应的响应状态和用户可读原因. */
  public ActivityException(int status, String message) {
    super(message);
    this.status = status;
  }

  /** 返回控制器应采用的 HTTP 响应状态码. */
  public int status() {
    return status;
  }
}
