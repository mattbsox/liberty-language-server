package io.openliberty.lemminx.liberty;

import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.services.extensions.IHoverParticipant;
import org.eclipse.lemminx.services.extensions.IHoverRequest;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import io.openliberty.lemminx.liberty.models.feature.*;
import io.openliberty.lemminx.liberty.services.FeatureService;
import io.openliberty.lemminx.liberty.services.SettingsService;
import io.openliberty.lemminx.liberty.util.*;

import java.util.Optional;
import java.util.logging.Logger;

public class LibertyHoverParticipant implements IHoverParticipant {

	private static final Logger LOGGER = Logger.getLogger(LibertyHoverParticipant.class.getName());

	@Override
	public Hover onAttributeName(IHoverRequest request) {
		return null;
	}

	@Override
	public Hover onAttributeValue(IHoverRequest request) {
		return null;
	}

	@Override
	public Hover onTag(IHoverRequest request) {
		return null;
	}

	@Override
	public Hover onText(IHoverRequest request) {

		LOGGER.info("Running Hover");

		if (!LibertyUtils.isServerXMLFile(request.getXMLDocument()))
			return null;

		DOMElement parentElement = request.getParentElement();
		if (parentElement == null || parentElement.getTagName() == null)
			return null;

		// if we are hovering over text inside a <feature> element
		if (LibertyConstants.FEATURE_ELEMENT.equals(parentElement.getTagName())) {
			String featureName = request.getNode().getTextContent();
			return getHoverFeatureDescription(featureName);
		}

		return null;
	}

	private Hover getHoverFeatureDescription(String featureName) {
		final String libertyVersion = SettingsService.getInstance().getLibertyVersion();
		final int requestDelay = SettingsService.getInstance().getRequestDelay();
		Optional<Feature> feature = FeatureService.getInstance().getFeature(featureName, libertyVersion, requestDelay);
		if (feature.isPresent()) {
			return new Hover(new MarkupContent("plaintext", feature.get().getShortDescription()));
		}

		return null;
	}
}