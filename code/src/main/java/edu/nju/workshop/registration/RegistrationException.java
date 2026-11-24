package edu.nju.workshop.registration;

/** 携带 HTTP 状态码和报名页面反馈的业务异常. */
public class RegistrationException extends RuntimeException {
  private final int status;
  private final RegistrationResult result;

  /** 根据响应状态和报名反馈构造业务异常. */
  public RegistrationException(int status, RegistrationResult result) {
    super(result.message());
    this.status = status;
    this.result = result;
  }

  /** 返回报名请求应使用的 HTTP 响应状态码. */
  public int status() {
    return status;
  }

  /** 返回失败提示及报名操作的最新展示状态. */
  public RegistrationResult result() {
    return result;
  }
}
