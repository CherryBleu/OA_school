package com.oaschool.service;

import com.oaschool.common.Rows;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private final JdbcTemplate jdbc;

  public DashboardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public double recalculate(UUID projectId) {
    Map<String, Object> row = jdbc.queryForMap(
        """
        SELECT COALESCE(
          ROUND(
            SUM(estimated_days * CASE WHEN status = 'done' THEN 100 ELSE residual_progress END)
            / NULLIF(SUM(estimated_days), 0),
            2
          ),
          0
        ) AS progress
        FROM oa_task
        WHERE project_id = ?
        """,
        projectId
    );
    double progress = Rows.dbl(row, "progress");
    jdbc.update("UPDATE oa_project SET project_progress = ?, updated_at = now() WHERE id = ?", progress, projectId);
    return progress;
  }

  public Map<String, Object> dashboard(UUID projectId) {
    double progress = recalculate(projectId);
    List<Map<String, Object>> statusCounts = jdbc.queryForList(
        """
        SELECT status, COUNT(*)::int AS count
        FROM oa_task
        WHERE project_id = ?
        GROUP BY status
        """,
        projectId
    ).stream().map(Rows::normalize).toList();

    List<Map<String, Object>> contribution = jdbc.queryForList(
        """
        SELECT u.name, COALESCE(SUM(t.estimated_days), 0)::float AS value
        FROM oa_task t
        JOIN sys_user u ON u.id = t.assignee_id
        WHERE t.project_id = ? AND t.status = 'done'
        GROUP BY u.name
        ORDER BY value DESC
        """,
        projectId
    ).stream().map(Rows::normalize).toList();

    List<Map<String, Object>> tasks = jdbc.queryForList(
        """
        SELECT status, estimated_days, residual_progress
        FROM oa_task
        WHERE project_id = ?
        ORDER BY created_at ASC
        """,
        projectId
    );

    double totalWeight = tasks.stream().mapToDouble(item -> Rows.dbl(item, "estimated_days")).sum();
    double completedWeight = 0;
    List<Map<String, Object>> burnDown = new ArrayList<>();
    for (int i = 0; i < tasks.size(); i++) {
      Map<String, Object> task = tasks.get(i);
      double ratio = "done".equals(task.get("status")) ? 1 : Rows.dbl(task, "residual_progress") / 100;
      completedWeight += Rows.dbl(task, "estimated_days") * ratio;
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("name", "T" + (i + 1));
      point.put("remaining", Math.max(0, round2(totalWeight - completedWeight)));
      burnDown.add(point);
    }

    return Map.of(
        "progress", progress,
        "statusCounts", statusCounts,
        "contribution", contribution,
        "burnDown", burnDown
    );
  }

  private double round2(double value) {
    return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
  }
}
