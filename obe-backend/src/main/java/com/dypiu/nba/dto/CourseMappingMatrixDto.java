package com.dypiu.nba.dto;

import com.dypiu.nba.entity.CoPoMapping;
import com.dypiu.nba.entity.CoPsoMapping;
import com.dypiu.nba.entity.CourseOutcome;
import com.dypiu.nba.entity.ProgrammeOutcome;
import com.dypiu.nba.entity.ProgrammeSpecificOutcome;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseMappingMatrixDto {
    private String masterCourseId;
    private String programmeBatchCourseId;
    private String masterProgrammeId;
    private List<CourseOutcome> cos;
    private List<ProgrammeOutcome> pos;
    private List<ProgrammeSpecificOutcome> psos;
    private List<CoPoMapping> poMappings;
    private List<CoPsoMapping> psoMappings;
    private Map<String, Object> poKeywordsStore;
    private Map<String, Object> psoKeywordsStore;
    private Map<String, Map<String, Integer>> matrix;
    private Map<String, java.math.BigDecimal> poAverages;
    private Map<String, java.math.BigDecimal> psoAverages;
}
