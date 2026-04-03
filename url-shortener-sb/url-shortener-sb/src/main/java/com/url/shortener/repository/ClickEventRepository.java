package com.url.shortener.repository;

import com.url.shortener.models.ClickEvent;
import com.url.shortener.models.UrlMapping;
import com.url.shortener.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    interface DailyClickCountProjection {
        LocalDate getClickDate();
        Long getCount();
    }

    List<ClickEvent> findByUrlMappingAndClickDateBetween(UrlMapping mapping, LocalDateTime startDate, LocalDateTime endDate);

    List<ClickEvent> findByUrlMappingInAndClickDateBetween(List<UrlMapping> urlMappings, LocalDateTime startDate, LocalDateTime endDate);

    @Query("""
            select function('date', ce.clickDate) as clickDate, count(ce.id) as count
            from ClickEvent ce
            where ce.urlMapping.id = :urlMappingId
              and ce.clickDate >= :startDate
              and ce.clickDate < :endDateExclusive
            group by function('date', ce.clickDate)
            order by function('date', ce.clickDate)
            """)
    List<DailyClickCountProjection> aggregateDailyClicksByUrlMappingId(
            @Param("urlMappingId") Long urlMappingId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDateExclusive") LocalDateTime endDateExclusive
    );

    @Query("""
            select function('date', ce.clickDate) as clickDate, count(ce.id) as count
            from ClickEvent ce
            where ce.urlMapping.user = :user
              and ce.clickDate >= :startDate
              and ce.clickDate < :endDateExclusive
            group by function('date', ce.clickDate)
            order by function('date', ce.clickDate)
            """)
    List<DailyClickCountProjection> aggregateDailyClicksByUser(
            @Param("user") User user,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDateExclusive") LocalDateTime endDateExclusive
    );

    long countByUrlMappingUserAndClickDateGreaterThanEqualAndClickDateLessThan(
            User user,
            LocalDateTime startDate,
            LocalDateTime endDateExclusive
    );
}