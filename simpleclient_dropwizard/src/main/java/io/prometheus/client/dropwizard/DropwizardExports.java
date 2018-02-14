package io.prometheus.client.dropwizard;


import io.dropwizard.metrics5.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Collect Dropwizard metrics from a MetricRegistry.
 */
public class DropwizardExports extends io.prometheus.client.Collector implements io.prometheus.client.Collector.Describable {
	private MetricRegistry registry;
	private static final Logger LOGGER = Logger.getLogger(DropwizardExports.class.getName());

	/**
	 * @param registry a metric registry to export in prometheus.
	 */
	public DropwizardExports(MetricRegistry registry) {
		this.registry = registry;
	}

	/**
	 * Export counter as Prometheus <a href="https://prometheus.io/docs/concepts/metric_types/#gauge">Gauge</a>.
	 */
	List<MetricFamilySamples> fromCounter(MetricName dropwizardName, Counter counter) {
		String name = sanitizeMetricName(dropwizardName);
		final LabelsContainer labelsContainer = LabelsContainer.fromMetricName(dropwizardName);

		MetricFamilySamples.Sample sample = new MetricFamilySamples.Sample(name, labelsContainer.getLabelNames(),
				labelsContainer.getLabelValues(), new Long(counter.getCount()).doubleValue());
		return Arrays.asList(new MetricFamilySamples(name, Type.GAUGE, getHelpMessage(dropwizardName, counter), Arrays.asList(sample)));
	}

	private static String getHelpMessage(MetricName metricName, Metric metric) {
		return String.format("Generated from Dropwizard metric import (metric=%s, type=%s)",
				metricName.getKey(), metric.getClass().getName());
	}

	/**
	 * Export gauge as a prometheus gauge.
	 */
	List<MetricFamilySamples> fromGauge(MetricName dropwizardName, Gauge gauge) {
		String name = sanitizeMetricName(dropwizardName);
		Object obj = gauge.getValue();
		double value;
		final LabelsContainer labelsContainer = LabelsContainer.fromMetricName(dropwizardName);

		if (obj instanceof Number) {
			value = ((Number) obj).doubleValue();
		} else if (obj instanceof Boolean) {
			value = ((Boolean) obj) ? 1 : 0;
		} else {
			LOGGER.log(Level.FINE, String.format("Invalid type for Gauge %s: %s", name,
					obj.getClass().getName()));
			return new ArrayList<MetricFamilySamples>();
		}
		MetricFamilySamples.Sample sample = new MetricFamilySamples.Sample(name,
				labelsContainer.getLabelNames(), labelsContainer.getLabelValues(), value);
		return Arrays.asList(new MetricFamilySamples(name, Type.GAUGE, getHelpMessage(dropwizardName, gauge), Arrays.asList(sample)));
	}

	/**
	 * Export a histogram snapshot as a prometheus SUMMARY.
	 *
	 * @param dropwizardName metric name.
	 * @param snapshot       the histogram snapshot.
	 * @param count          the total sample count for this snapshot.
	 * @param factor         a factor to apply to histogram values.
	 */
	List<MetricFamilySamples> fromSnapshotAndCount(MetricName dropwizardName, Snapshot snapshot, long count, double factor, String helpMessage) {
		String name = sanitizeMetricName(dropwizardName);
		LabelsContainer labelsContainer = LabelsContainer.fromMetricName(dropwizardName);
		List<MetricFamilySamples.Sample> samples = new ArrayList<MetricFamilySamples.Sample>();
		LabelsContainer toAssign;

		toAssign = labelsContainer.copyAddingLabel("quantile", "0.5");
		samples.add(new MetricFamilySamples.Sample(name, toAssign.getLabelNames(), toAssign.getLabelValues(),
				snapshot.getMedian() * factor));
		toAssign = labelsContainer.copyAddingLabel("quantile", "0.75");
		samples.add(new MetricFamilySamples.Sample(name, toAssign.getLabelNames(), toAssign.getLabelValues(),
				snapshot.get75thPercentile() * factor));
		toAssign = labelsContainer.copyAddingLabel("quantile", "0.95");
		samples.add(new MetricFamilySamples.Sample(name, toAssign.getLabelNames(), toAssign.getLabelValues(),
				snapshot.get95thPercentile() * factor));
		toAssign = labelsContainer.copyAddingLabel("quantile", "0.98");
		samples.add(new MetricFamilySamples.Sample(name, toAssign.getLabelNames(), toAssign.getLabelValues(),
				snapshot.get98thPercentile() * factor));
		toAssign = labelsContainer.copyAddingLabel("quantile", "0.99");
		samples.add(new MetricFamilySamples.Sample(name, toAssign.getLabelNames(), toAssign.getLabelValues(),
				snapshot.get99thPercentile() * factor));
		toAssign = labelsContainer.copyAddingLabel("quantile", "0.999");
		samples.add(new MetricFamilySamples.Sample(name, toAssign.getLabelNames(), toAssign.getLabelValues(),
				snapshot.get999thPercentile() * factor));

		samples.add(new MetricFamilySamples.Sample(name + "_count", new ArrayList<String>(), new ArrayList<String>(), count));

		return Arrays.asList(
				new MetricFamilySamples(name, Type.SUMMARY, helpMessage, samples)
		);
	}

	/**
	 * Convert histogram snapshot.
	 */
	List<MetricFamilySamples> fromHistogram(MetricName dropwizardName, Histogram histogram) {
		return fromSnapshotAndCount(dropwizardName, histogram.getSnapshot(), histogram.getCount(), 1.0,
				getHelpMessage(dropwizardName, histogram));
	}

	/**
	 * Export Dropwizard Timer as a histogram. Use TIME_UNIT as time unit.
	 */
	List<MetricFamilySamples> fromTimer(MetricName dropwizardName, Timer timer) {
		return fromSnapshotAndCount(dropwizardName, timer.getSnapshot(), timer.getCount(),
				1.0D / TimeUnit.SECONDS.toNanos(1L), getHelpMessage(dropwizardName, timer));
	}

	/**
	 * Export a Meter as as prometheus COUNTER.
	 */
	List<MetricFamilySamples> fromMeter(MetricName dropwizardName, Meter meter) {
		String name = sanitizeMetricName(dropwizardName);
		LabelsContainer labelsContainer = LabelsContainer.fromMetricName(dropwizardName);

		return Arrays.asList(
				new MetricFamilySamples(name + "_total", Type.COUNTER, getHelpMessage(dropwizardName, meter),
						Arrays.asList(new MetricFamilySamples.Sample(name + "_total",
								labelsContainer.getLabelNames(),
								labelsContainer.getLabelValues(),
								meter.getCount())))

		);
	}

	private static final Pattern METRIC_NAME_RE = Pattern.compile("[^a-zA-Z0-9:_]");

	/**
	 * Replace all unsupported chars with '_', prepend '_' if name starts with digit.
	 *
	 * @param dropwizardName original metric name.
	 * @return the sanitized metric name.
	 */
	public static String sanitizeMetricName(MetricName dropwizardName) {
		String name = METRIC_NAME_RE.matcher(dropwizardName.getKey()).replaceAll("_");
		if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
			name = "_" + name;
		}
		return name;
	}

	@Override
	public List<MetricFamilySamples> collect() {
		ArrayList<MetricFamilySamples> mfSamples = new ArrayList<MetricFamilySamples>();
		for (SortedMap.Entry<MetricName, Gauge> entry : registry.getGauges().entrySet()) {
			mfSamples.addAll(fromGauge(entry.getKey(), entry.getValue()));
		}
		for (SortedMap.Entry<MetricName, Counter> entry : registry.getCounters().entrySet()) {
			mfSamples.addAll(fromCounter(entry.getKey(), entry.getValue()));
		}
		for (SortedMap.Entry<MetricName, Histogram> entry : registry.getHistograms().entrySet()) {
			mfSamples.addAll(fromHistogram(entry.getKey(), entry.getValue()));
		}
		for (SortedMap.Entry<MetricName, Timer> entry : registry.getTimers().entrySet()) {
			mfSamples.addAll(fromTimer(entry.getKey(), entry.getValue()));
		}
		for (SortedMap.Entry<MetricName, Meter> entry : registry.getMeters().entrySet()) {
			mfSamples.addAll(fromMeter(entry.getKey(), entry.getValue()));
		}
		return mfSamples;
	}

	@Override
	public List<MetricFamilySamples> describe() {
		return new ArrayList<MetricFamilySamples>();
	}
}
