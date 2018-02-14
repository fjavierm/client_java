package io.prometheus.client.dropwizard;

import io.dropwizard.metrics5.MetricName;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

public class LabelsContainer {

	private List<String> labelNames;
	private List<String> labelValues;

	public LabelsContainer() {
		this.labelNames = new ArrayList<String>();
		this.labelValues = new ArrayList<String>();
	}

	public static LabelsContainer fromMetricName(MetricName metricName) {
		final LabelsContainer labelsContainer = new LabelsContainer();

		for (final SortedMap.Entry<String, String> entry : metricName.getTags().entrySet()) {
			labelsContainer.labelNames.add(entry.getKey());
			labelsContainer.labelValues.add(entry.getValue());
		}

		return labelsContainer;
	}

	public LabelsContainer copyAddingLabel(String name, String value) {
		final LabelsContainer labelsContainer = new LabelsContainer();

		labelsContainer.labelNames.addAll(this.labelNames);
		labelsContainer.labelValues.addAll(this.labelValues);

		labelsContainer.labelNames.add(name);
		labelsContainer.labelValues.add(value);

		return labelsContainer;
	}

	public List<String> getLabelNames() {
		return labelNames;
	}

	public List<String> getLabelValues() {
		return labelValues;
	}
}
