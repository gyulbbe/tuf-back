package io.github.gyulbbe.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeMainResponse {
    private HomeScheduleResponse notice;

    @Builder.Default
    private List<HomeScheduleResponse> proleagueSchedules = new ArrayList<>();

    @Builder.Default
    private List<HomeScheduleResponse> personalLeagueSchedules = new ArrayList<>();

    @Builder.Default
    private List<HomeMainOngoingResponse> ongoing = new ArrayList<>();

    @Builder.Default
    private List<HomeMainBotAlertResponse> botAlerts = new ArrayList<>();

    @Builder.Default
    private List<HomeMainGalleryPostResponse> galleryPosts = new ArrayList<>();

    @Builder.Default
    private List<HomeScheduleResponse> schedules = new ArrayList<>();
}
