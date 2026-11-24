package edu.nju.workshop.shared;

import edu.nju.workshop.activity.ActivityException;
import edu.nju.workshop.identity.AccountUser;
import edu.nju.workshop.identity.Role;
import edu.nju.workshop.statistics.ManagementScopeRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 校验管理查询的角色和对象归属，并提供可选活动及场次. */
@Service
public class ManagementScopeService {
  private final ManagementScopeRepository repository;

  /** 使用管理范围仓库读取活动和场次归属. */
  public ManagementScopeService(ManagementScopeRepository repository) {
    this.repository = repository;
  }

  /** 校验管理身份及筛选对象，生成后续查询共用的授权范围. */
  public ManagementScope resolve(AccountUser user, Long activityId, Long sessionId) {
    requireManager(user);
    Long ownerId = user.getRole() == Role.ORGANIZER ? user.getId() : null;
    if (activityId != null) {
      requireTarget(repository.activity(activityId), ownerId, "活动");
    }
    if (sessionId != null) {
      var session = repository.session(sessionId);
      requireTarget(session, ownerId, "场次");
      if (activityId != null && !Objects.equals(activityId, session.activityId())) {
        throw new ActivityException(400, "所选场次不属于该活动");
      }
    }
    return new ManagementScope(ownerId, activityId, sessionId);
  }

  /** 返回当前管理者有权查看的活动选项. */
  public List<Choice> activities(ManagementScope scope) {
    return repository.activities(new ManagementScope(scope.ownerId(), null, null));
  }

  /** 返回当前管理者在所选活动范围内有权查看的场次选项. */
  public List<Choice> sessions(ManagementScope scope) {
    return repository.sessions(new ManagementScope(scope.ownerId(), scope.activityId(), null));
  }

  /** 根据已授权的管理角色返回对应页面路径前缀. */
  public String basePath(AccountUser user) {
    requireManager(user);
    return user.getRole() == Role.ADMIN ? "/admin" : "/organizer";
  }

  private void requireManager(AccountUser user) {
    if (user == null || (user.getRole() != Role.ADMIN && user.getRole() != Role.ORGANIZER)) {
      throw new ActivityException(403, "无权访问管理查询");
    }
  }

  private void requireTarget(ManagementScopeRepository.Target target, Long ownerId, String name) {
    if (target == null) {
      throw new ActivityException(400, "所选" + name + "不存在");
    }
    if (ownerId != null && ownerId != target.ownerId()) {
      throw new ActivityException(403, "无权访问该" + name);
    }
  }

  /** 管理查询筛选框中的对象编号和展示名称. */
  public record Choice(long id, String label) {
  }
}
