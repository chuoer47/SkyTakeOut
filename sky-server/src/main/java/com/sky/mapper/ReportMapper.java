package com.sky.mapper;

import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.ReportDTO;
import com.sky.vo.SalesTop10ReportVO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReportMapper {

    List<Double> getTurnoverStatistics(ReportDTO reportDTO);

    Integer getNewUser(LocalDate time);

    Integer getTotalUser(LocalDate time);

    Integer countOrder(LocalDate time);

    Integer countValidOrder(LocalDate time);

    List<GoodsSalesDTO> top10(ReportDTO reportDTO);
}
