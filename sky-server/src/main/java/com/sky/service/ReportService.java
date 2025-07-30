package com.sky.service;

import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.ReportDTO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import javax.servlet.http.HttpServletResponse;

public interface ReportService {
    TurnoverReportVO getTurnoverStatistics(ReportDTO reportDTO);

    UserReportVO getUserStatistics(ReportDTO reportDTO);

    OrderReportVO getOrderStatistics(ReportDTO reportDTO);

    SalesTop10ReportVO top10(ReportDTO reportDTO);

    void exportBusinessData(HttpServletResponse httpServletResponse);
}
