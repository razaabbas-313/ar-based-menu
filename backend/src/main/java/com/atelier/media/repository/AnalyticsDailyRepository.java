package com.atelier.media.repository;

import com.atelier.media.model.AnalyticsDaily;
import com.atelier.media.model.AnalyticsDailyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AnalyticsDailyRepository extends JpaRepository<AnalyticsDaily, AnalyticsDailyId> {

    List<AnalyticsDaily> findByRestaurantIdAndDayBetween(Long restaurantId, LocalDate start, LocalDate end);
}
