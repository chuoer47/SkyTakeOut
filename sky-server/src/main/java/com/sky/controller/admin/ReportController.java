package com.sky.controller.admin;

import com.sky.dto.ReportDTO;
import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

/**
 * 数据统计
 */
@Slf4j
@RestController
@RequestMapping("/admin/report")
@Api(tags = "数据统计相关接口")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @ApiOperation("营业额统计")
    @GetMapping("/turnoverStatistics")
    public Result<TurnoverReportVO> turnoverStatistics(ReportDTO reportDTO) {
        log.info("营业额数据统计：{}", reportDTO);
        TurnoverReportVO turnoverStatistics = reportService.getTurnoverStatistics(reportDTO);
        return Result.success(turnoverStatistics);
    }

    @GetMapping("/userStatistics")
    @ApiOperation("用户统计")
    public Result<UserReportVO> userStatistics(ReportDTO reportDTO) {
        log.info("用户数据统计：{}", reportDTO);
        UserReportVO userReportVO = reportService.getUserStatistics(reportDTO);
        return Result.success(userReportVO);
    }

    @GetMapping("/ordersStatistics")
    @ApiOperation("订单统计")
    public Result<OrderReportVO> ordersStatistics(ReportDTO reportDTO) {
        log.info("订单数据统计：{}", reportDTO);
        OrderReportVO orderReportVO = reportService.getOrderStatistics(reportDTO);
        return Result.success(orderReportVO);
    }

    @GetMapping("/top10")
    @ApiOperation("销量top10")
    public Result<SalesTop10ReportVO> top10(ReportDTO reportDTO) {
        log.info("查询top10：{}", reportDTO);

        SalesTop10ReportVO top10 = reportService.top10(reportDTO);
        return Result.success(top10);
    }

    @GetMapping("/export")
    @ApiOperation("导出报表")
    public Result export(HttpServletResponse httpServletResponse) {
        log.info("导出近30天的运营数据报表");
        reportService.exportBusinessData(httpServletResponse);
        return Result.success();
    }
}
