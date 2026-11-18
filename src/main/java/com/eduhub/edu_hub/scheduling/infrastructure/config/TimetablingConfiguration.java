package com.eduhub.edu_hub.scheduling.infrastructure.config;

import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.SolverManagerConfig;
import com.eduhub.edu_hub.scheduling.infrastructure.solver.TimetableScoreCalculator;
import com.eduhub.edu_hub.scheduling.infrastructure.solver.TimetableSolution;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(TimetablingSettings.class)
public class TimetablingConfiguration {

    @Bean
    public ThreadPoolTaskExecutor timetablePlanExecutor() {

        // solver rất tốn CPU, nên giới hạn chặt: tối đa 1 đang chạy + 1 đang chờ
        // Tác vụ thứ ba sẽ bị từ chối với TaskRejectedException (chính sách mặc định AbortPolicy)

        // TODO: bắt TaskRejectedException ở service để trả lỗi dễ hiểu (“Hệ thống đang bận, thử lại sau”).

        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("timetable-plan-");
        return executor;
    }

    @Bean(destroyMethod = "close")
    public SolverManager<TimetableSolution> timetableSolverManager(TimetablingSettings settings) {
        return SolverManager.create(
                solverConfig(Duration.ofSeconds(settings.getSpentLimitSeconds())),
                new SolverManagerConfig().withParallelSolverCount("1"));
    }

    // mô tả bài toán cho Solver
    public static SolverConfig solverConfig(Duration spentLimit) {
        return new SolverConfig()
                .withSolutionClass(TimetableSolution.class)
                .withEntityClasses(
                        TimetableSolution.PhaseBoundary.class,
                        TimetableSolution.SubjectAssignment.class,
                        TimetableSolution.ClassAssignment.class,
                        TimetableSolution.SessionAssignment.class)
                .withEasyScoreCalculatorClass(TimetableScoreCalculator.class)
                .withTerminationSpentLimit(spentLimit);
    }
}
