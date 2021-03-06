package com.neverpile.fusion.model.rules.javascript;

import static java.util.function.Function.identity;

import java.io.InputStreamReader;
import java.util.stream.Collectors;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neverpile.fusion.model.Collection;
import com.neverpile.fusion.model.CollectionType;
import com.neverpile.fusion.model.Element;
import com.neverpile.fusion.model.rules.CollectionLayout;
import com.neverpile.fusion.model.rules.Node;
import com.neverpile.fusion.model.rules.Rule;
import com.neverpile.fusion.model.rules.RuleExecutionException;
import com.neverpile.fusion.model.rules.ViewLayout;

/**
 * A JavaScript based layout engine. It can make use of {@link Rule} of the type
 * {@link JavascriptRule}.
 */
@Component
public class JavascriptViewLayoutEngine {
  private static final Logger LOGGER = LoggerFactory.getLogger(JavascriptViewLayoutEngine.class);

  private final ObjectMapper objectMapper;
  private final ScriptEngine engine;

  @Autowired
  public JavascriptViewLayoutEngine(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;

    this.engine = new ScriptEngineManager().getEngineByName("JavaScript");
  }

  /**
   * Create layouts for all views in the given collection type.
   * 
   * @param collection the collection for which to generate layouts
   * @param type the collection type (must match the type of the collection)
   * @return a list of {@link ViewLayout}s. One for each defined view type.
   * @throws RuleExecutionException if the rule execution fails
   */
  public CollectionLayout layoutTree(final Collection collection, final CollectionType type)
      throws RuleExecutionException {

    try {
      CollectionLayout layout = new CollectionLayout();
      layout.setCollectionTypeId(type.getId());
      
      Bindings bindings = preparcollectionProcessingBindings(collection, layout);

      // apply library rules
      type.getGlobalRules().forEach(lib -> apply(bindings, (JavascriptRule) lib));
      
      layout.setViewLayouts(type.getViews().stream().map(view -> {
        Node root = new Node();
        root.setName("root");

        // bind root node
        bindings.put("_root", root);

        try {
          // apply node creation rules
          collection.getElements().forEach(element -> view.getElementRules().forEach(
              rule -> apply(bindings, (JavascriptRule) rule, collection, element)));

          // apply tree rules
          view.getTreeRules().forEach(rule -> apply(bindings, (JavascriptRule) rule));

          return new ViewLayout(view.getName(), root);
        } catch (Exception e) {
          // don't throw
          LOGGER.info("Failed to lay out view: {} for type {}", view.getName(), type.getName(), e);
          return new ViewLayout(view.getName(), "Failed to lay out view: " + e.getMessage());
        }
      }).collect(Collectors.toMap(ViewLayout::getView, identity())));
      
      return layout;
    } catch (ScriptException e) {
      throw new RuleExecutionException("global", "Failed to initialize tree layout engine", e);
    } catch (JsonProcessingException e) {
      throw new RuleExecutionException("global", "Cannot convert collection to JSON", e);
    }
  }

  private Bindings preparcollectionProcessingBindings(final Collection collection, final CollectionLayout layout)
      throws ScriptException, JsonProcessingException {
    Bindings bindings = engine.createBindings();

    // load scripting support
    engine.eval(new InputStreamReader(getClass().getResourceAsStream("LayoutEngine.js")), bindings);

    // intialize private bindings
    bindings.put("_collection", collection);
    bindings.put("_layout", layout);

    // expose the collection as JSON to the scripting context
    engine.eval("var collection = " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(collection),
        bindings);

    // run per-collection initialization
    engine.eval("_initCollection()", bindings);

    return bindings;
  }

  private void apply(final Bindings bindings, final JavascriptRule rule, final Collection collection,
      final Element element) {
    if (null == rule.getScriptCode())
      return; // nothing to do

    try {
      // intialize private bindings
      bindings.put("_elementIndex", collection.getElements().indexOf(element));

      // run per-element initialization
      engine.eval("_initElement()", bindings);

      // run rule script
      engine.eval(rule.getScriptCode(), bindings);
    } catch (Exception e) {
      LOGGER.debug("Failed to execute JavaScript-based rule '{}'", rule.getName(), e);
      throw new RuleExecutionException(rule.getName(),
          "Failed to execute JavaScript-based rule '" + rule.getName() + "': " + e.getMessage(), e);
    }
  }

  public void apply(final Bindings bindings, final JavascriptRule rule) {
    if (null == rule.getScriptCode())
      return; // nothing to do

    try {
      engine.eval(rule.getScriptCode(), bindings);
    } catch (Exception e) {
      throw new RuleExecutionException(rule.getName(),
          "Failed to execute JavaScript-based rule " + rule.getName() + ": " + e.getMessage(), e);
    }
  }
}
