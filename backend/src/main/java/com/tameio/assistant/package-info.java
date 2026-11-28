/**
 * The business's assistant: questions in plain Greek about its account, expenses, loans and the bank's terms,
 * answered by the assistant service (Python, a LangGraph agent on Claude). This module checks who is asking and
 * passes the question on with their business, so the service never takes the business from the user or the model.
 */
@ApplicationModule(displayName = "Assistant")
package com.tameio.assistant;

import org.springframework.modulith.ApplicationModule;
