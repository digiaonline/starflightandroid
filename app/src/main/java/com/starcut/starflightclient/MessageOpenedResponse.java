package com.starcut.starflightclient;

public class MessageOpenedResponse implements StarFlightResponse
{
	private final Result result;

	MessageOpenedResponse(Result result)
	{
		this.result = result;
	}

	public Result getResult()
	{
		return result;
	}

	public static enum Result
	{
		/**
		 * The message open was recorded successfully
		 */
		OK,

		/**
		 * The opening of the message had already been recorded
		 */
		ALREADY_OPENED;
	}
}
