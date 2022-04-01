package me.elyar.redisland.gui.controller.tabs;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.util.StringConverter;
import me.elyar.redisland.redis.RedisInfoUtil;
import me.elyar.redisland.client.RedisClient;
import me.elyar.redisland.util.Language;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PerformanceTabController {
    public AreaChart<Number, Number> connectedClientChart;
    public AreaChart<Number, Number> networkInputChart;
    public AreaChart<Number, Number> networkOutputChart;
    public AreaChart<Number, Number> usedMemoryChart;
    public AreaChart<Number, Number> optsPerSecChart;
    public AreaChart<Number, Number> hitMissChart;

    public DoubleProperty hitRate = new SimpleDoubleProperty();
    public StringProperty memoryUsage = new SimpleStringProperty();
    public IntegerProperty totalConnection = new SimpleIntegerProperty();
    public IntegerProperty rejectedConnection = new SimpleIntegerProperty();
    public StringProperty upTime = new SimpleStringProperty();
    public IntegerProperty clientCount = new SimpleIntegerProperty();
    public StringProperty totalKeyCount = new SimpleStringProperty();

    public Label hitRateLabel;
    public Label memoryLabel;
    public Label totalConnectionLabel;
    public Label connectionRejectedLabel;
    public Label uptimeLabel;
    public Label clientCountLabel;
    public Label totalKeyCountLabel;

    private transient boolean closed = false;

    int xAxisLength = 61; // in seconds
    int fps = 5; // hz

    private final int period = 1000 / fps; // in milliseconds
    private final int xCount = fps * xAxisLength;

    class ChartWrapper {
        private final XYChart.Series<Number, Number> series;

        public ChartWrapper(XYChart<Number, Number> chart, String name) {
            NumberAxis xAxis = (NumberAxis) chart.getXAxis();
            NumberAxis yAxis = (NumberAxis) chart.getYAxis();
            series = new XYChart.Series<>();

            chart.getData().add(series);

            yAxis.setForceZeroInRange(true);

            xAxis.setForceZeroInRange(false);
            xAxis.setAutoRanging(false);
            chart.setAnimated(false);

            xAxis.setLowerBound((double) (xCount - 1) / fps);
            xAxis.setUpperBound(0);
            series.setName(name);
            series.getNode().getStyleClass().add(name);
            yAxis.setTickLabelFormatter(new StringConverter<>() {
                @Override
                public String toString(Number number) {
                    if (number.doubleValue() == 0) {
                        return "0";
                    } else if (number.longValue() > 1024) {
                        return withSuffix(number.doubleValue());
                    } else {
                        return String.format("%.2f", number.doubleValue());
                    }
                }

                @Override
                public Number fromString(String string) {
                    return null;
                }
            });
        }

        AtomicInteger currentDataIndex = new AtomicInteger(0);


        public void addData(Number data) {
            ObservableList<XYChart.Data<Number, Number>> currentDataList = series.getData();
            if (currentDataList.size() >= xCount) {
                currentDataList.remove(xCount - 1);
            }

            for (XYChart.Data<Number, Number> x : currentDataList) {
                x.setXValue(1.0 / fps + (double) x.getXValue());
            }
            currentDataList.add(0, new XYChart.Data<>(0.0, data.doubleValue()));

            currentDataIndex.incrementAndGet();
        }
    }

    public void init(RedisClient client) {
        hitRateLabel.textProperty().bind(hitRate.asString("%.2f"));
        memoryLabel.textProperty().bind(memoryUsage);
        totalConnectionLabel.textProperty().bind(totalConnection.asString());
        connectionRejectedLabel.textProperty().bind(rejectedConnection.asString());
        uptimeLabel.textProperty().bind(upTime);
        clientCountLabel.textProperty().bind(clientCount.asString());
        totalKeyCountLabel.textProperty().bind(totalKeyCount);

        ChartWrapper connectedClientChartWrapper = new ChartWrapper(connectedClientChart, "client-count-series");
        ChartWrapper networkInputChartWrapper = new ChartWrapper(networkInputChart, "net-in-series");
        ChartWrapper networkOutputChartWrapper = new ChartWrapper(networkOutputChart, "net-out-series");
        ChartWrapper usedMemoryChartWrapper = new ChartWrapper(usedMemoryChart, "used-mem-series");
        ChartWrapper optsPerSecChartWrapper = new ChartWrapper(optsPerSecChart, "opts-per-sec-series");
        ChartWrapper hitChartWrapper = new ChartWrapper(hitMissChart, "hit-series");
        ChartWrapper missChartWrapper = new ChartWrapper(hitMissChart, "miss-series");

        Timer timer = new Timer();
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            if (closed) {
                                timer.cancel();
                            }
                            Map<String, String> info = client.info();
                            int connectedClients = Integer.parseInt(info.get("connected_clients"));
                            double instantaneousInputKbps = Double.parseDouble(info.get("instantaneous_input_kbps")) * 1024;
                            double instantaneousOutputKbps = Double.parseDouble(info.get("instantaneous_output_kbps")) * 1024;
                            double usedMemory = Double.parseDouble(info.get("used_memory"));
                            int opsPerSec = Integer.parseInt(info.get("instantaneous_ops_per_sec"));

                            int hits = Integer.parseInt(info.get("keyspace_hits"));
                            int misses = Integer.parseInt(info.get("keyspace_misses"));


                            double _hitRate =
                                    ((hits + misses) != 0) ? ((double) hits) / (hits + misses) : 1;

                            String memoryHuman = info.get("used_memory_human");
                            int totalConnectionsReceived = Integer.parseInt(info.get("total_connections_received"));
                            int rejected = Integer.parseInt(info.get("rejected_connections"));
                            int uptimeInSeconds = Integer.parseInt(info.get("uptime_in_seconds"));
                            String formattedUptime = formatSecond(uptimeInSeconds);
                            int keyCount = RedisInfoUtil.totalKeyCount(info);
                            Platform.runLater(() -> {
                                hitRate.set(_hitRate);
                                memoryUsage.set(memoryHuman);
                                totalConnection.set(totalConnectionsReceived);
                                rejectedConnection.set(rejected);
                                upTime.set(formattedUptime);
                                clientCount.set(connectedClients);
                                totalKeyCount.set(withSuffix(keyCount));

                                connectedClientChartWrapper.addData(connectedClients);
                                networkInputChartWrapper.addData(instantaneousInputKbps);
                                networkOutputChartWrapper.addData(instantaneousOutputKbps);
                                usedMemoryChartWrapper.addData(usedMemory);
                                optsPerSecChartWrapper.addData(opsPerSec);

                                hitChartWrapper.addData(hits);
                                missChartWrapper.addData(misses);
                            });

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, 0, period);


    }

    public void close() {
        closed = true;
    }

    private static final String TIME_UNIT_DAY = Language.getString("redis_performance_up_time_day");
    private static final String TIME_UNIT_DAYS = Language.getString("redis_performance_up_time_days");
    private static final String TIME_UNIT_HOUR = Language.getString("redis_performance_up_time_hour");
    private static final String TIME_UNIT_HOURS = Language.getString("redis_performance_up_time_hours");
    private static final String TIME_UNIT_MINUTE = Language.getString("redis_performance_up_time_minute");
    private static final String TIME_UNIT_MINUTES = Language.getString("redis_performance_up_time_minutes");
    private static final String TIME_UNIT_SECOND = Language.getString("redis_performance_up_time_second");
    private static final String TIME_UNIT_SECONDS = Language.getString("redis_performance_up_time_seconds");

    public static String formatSecond(long second) {
        String formattedSecond;
        long days = second / (60 * 60 * 24);
        long hours = (second % (60 * 60 * 24)) / (60 * 60);
        long minutes = (second % (60 * 60)) / 60;
        long seconds = second % 60;
        if (days > 0) {
            formattedSecond = days + (days > 1 ? TIME_UNIT_DAYS : TIME_UNIT_DAY);
        } else if (hours > 0) {
            formattedSecond = hours + (hours > 1 ? TIME_UNIT_HOURS : TIME_UNIT_HOUR);
        } else if (minutes > 0) {
            formattedSecond = minutes + (minutes > 1 ? TIME_UNIT_MINUTES : TIME_UNIT_MINUTE);
        } else {
            formattedSecond = seconds + (seconds > 1 ? TIME_UNIT_SECONDS : TIME_UNIT_SECOND);
        }

        return formattedSecond;
    }

    public static String withSuffix(double count) {
        if (count < 1000) return "" + count;
        int exp = (int) (Math.log(count) / Math.log(1000));
        return String.format("%.1f %c",
                count / Math.pow(1000, exp),
                "kMGTPE".charAt(exp - 1));
    }

}
