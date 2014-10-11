/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.simonvt.numberpicker;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.LayoutInflater.Filter;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A widget that enables the user to select a number form a predefined range.
 * There are two flavors of this widget and which one is presented to the user
 * depends on the current theme.
 * <ul>
 * <li>
 * If the current theme is derived from {@link android.R.style#Theme} the widget
 * presents the current value as an editable input field with an increment
 * button above and a decrement button below. Long pressing the buttons allows
 * for a quick change of the current value. Tapping on the input field allows to
 * type in a desired value.</li>
 * <li>
 * If the current theme is derived from {@link android.R.style#Theme_Holo} or
 * {@link android.R.style#Theme_Holo_Light} the widget presents the current
 * value as an editable input field with a lesser value above and a greater
 * value below. Tapping on the lesser or greater value selects it by animating
 * the number axis up or down to make the chosen value current. Flinging up or
 * down allows for multiple increments or decrements of the current value. Long
 * pressing on the lesser and greater values also allows for a quick change of
 * the current value. Tapping on the current value allows to type in a desired
 * value.</li>
 * </ul>
 * <p>
 * For an example of using this widget, see {@link android.widget.TimePicker}.
 * </p>
 */
// @Widget
public class NumberPicker extends LinearLayout {

	/**
	 * Class for managing virtual view tree rooted at this picker.
	 */
	@SuppressLint( "NewApi" )
	class AccessibilityNodeProviderImpl extends AccessibilityNodeProvider {
		private static final int UNDEFINED = Integer.MIN_VALUE;

		private static final int VIRTUAL_VIEW_ID_INCREMENT = 1;

		private static final int VIRTUAL_VIEW_ID_INPUT = 2;

		private static final int VIRTUAL_VIEW_ID_DECREMENT = 3;

		private final Rect mTempRect = new Rect();

		private final int[] mTempArray = new int[ 2 ];

		private int mAccessibilityFocusedView =
				AccessibilityNodeProviderImpl.UNDEFINED;

		@Override
		public AccessibilityNodeInfo createAccessibilityNodeInfo(
				final int virtualViewId ) {
			switch ( virtualViewId ) {
			case View.NO_ID:
				return this.createAccessibilityNodeInfoForNumberPicker(
						NumberPicker.this.getScrollX(),
						NumberPicker.this.getScrollY(),
						NumberPicker.this.getScrollX()
								+ ( NumberPicker.this.getRight() - NumberPicker.this.getLeft() ),
						NumberPicker.this.getScrollY()
								+ ( NumberPicker.this.getBottom() - NumberPicker.this.getTop() ) );
			case VIRTUAL_VIEW_ID_DECREMENT:
				return this.createAccessibilityNodeInfoForVirtualButton(
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_DECREMENT,
						this.getVirtualDecrementButtonText(),
						NumberPicker.this.getScrollX(),
						NumberPicker.this.getScrollY(),
						NumberPicker.this.getScrollX()
								+ ( NumberPicker.this.getRight() - NumberPicker.this.getLeft() ),
						NumberPicker.this.mTopSelectionDividerTop
								+ NumberPicker.this.mSelectionDividerHeight );
			case VIRTUAL_VIEW_ID_INPUT:
				return this.createAccessibiltyNodeInfoForInputText();
			case VIRTUAL_VIEW_ID_INCREMENT:
				return this.createAccessibilityNodeInfoForVirtualButton(
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INCREMENT,
						this.getVirtualIncrementButtonText(),
						NumberPicker.this.getScrollX(),
						NumberPicker.this.mBottomSelectionDividerBottom
								- NumberPicker.this.mSelectionDividerHeight,
						NumberPicker.this.getScrollX()
								+ ( NumberPicker.this.getRight() - NumberPicker.this.getLeft() ),
						NumberPicker.this.getScrollY()
								+ ( NumberPicker.this.getBottom() - NumberPicker.this.getTop() ) );
			}
			return super.createAccessibilityNodeInfo( virtualViewId );
		}

		private AccessibilityNodeInfo createAccessibilityNodeInfoForNumberPicker(
				final int left, final int top, final int right, final int bottom ) {
			final AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
			info.setClassName( NumberPicker.class.getName() );
			info.setPackageName( NumberPicker.this.getContext().getPackageName() );
			info.setSource( NumberPicker.this );

			if ( this.hasVirtualDecrementButton() ) {
				info.addChild( NumberPicker.this,
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_DECREMENT );
			}
			info.addChild( NumberPicker.this,
					AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INPUT );
			if ( this.hasVirtualIncrementButton() ) {
				info.addChild( NumberPicker.this,
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INCREMENT );
			}

			info.setParent( (View) NumberPicker.this.getParentForAccessibility() );
			info.setEnabled( NumberPicker.this.isEnabled() );
			info.setScrollable( true );

			/**
			 * TODO: Figure out compat implementation for this final float
			 * applicationScale =
			 * getContext().getResources().getCompatibilityInfo
			 * ().applicationScale;
			 * 
			 * Rect boundsInParent = mTempRect; boundsInParent.set(left, top,
			 * right, bottom); boundsInParent.scale(applicationScale);
			 * info.setBoundsInParent(boundsInParent);
			 * 
			 * info.setVisibleToUser(isVisibleToUser());
			 * 
			 * Rect boundsInScreen = boundsInParent; int[] locationOnScreen =
			 * mTempArray; getLocationOnScreen(locationOnScreen);
			 * boundsInScreen.offset(locationOnScreen[0], locationOnScreen[1]);
			 * boundsInScreen.scale(applicationScale);
			 * info.setBoundsInScreen(boundsInScreen);
			 */

			if ( this.mAccessibilityFocusedView != View.NO_ID ) {
				info.addAction( AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS );
			}
			if ( this.mAccessibilityFocusedView == View.NO_ID ) {
				info.addAction( AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS );
			}
			if ( NumberPicker.this.isEnabled() ) {
				if ( NumberPicker.this.getWrapSelectorWheel()
						|| ( NumberPicker.this.getValue() < NumberPicker.this.getMaxValue() ) ) {
					info.addAction( AccessibilityNodeInfo.ACTION_SCROLL_FORWARD );
				}
				if ( NumberPicker.this.getWrapSelectorWheel()
						|| ( NumberPicker.this.getValue() > NumberPicker.this.getMinValue() ) ) {
					info.addAction( AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD );
				}
			}

			return info;
		}

		private AccessibilityNodeInfo createAccessibilityNodeInfoForVirtualButton(
				final int virtualViewId, final String text, final int left,
				final int top, final int right, final int bottom ) {
			final AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
			info.setClassName( Button.class.getName() );
			info.setPackageName( NumberPicker.this.getContext().getPackageName() );
			info.setSource( NumberPicker.this, virtualViewId );
			info.setParent( NumberPicker.this );
			info.setText( text );
			info.setClickable( true );
			info.setLongClickable( true );
			info.setEnabled( NumberPicker.this.isEnabled() );
			final Rect boundsInParent = this.mTempRect;
			boundsInParent.set( left, top, right, bottom );
			// TODO info.setVisibleToUser(isVisibleToUser(boundsInParent));
			info.setBoundsInParent( boundsInParent );
			final Rect boundsInScreen = boundsInParent;
			final int[] locationOnScreen = this.mTempArray;
			NumberPicker.this.getLocationOnScreen( locationOnScreen );
			boundsInScreen.offset( locationOnScreen[ 0 ], locationOnScreen[ 1 ] );
			info.setBoundsInScreen( boundsInScreen );

			if ( this.mAccessibilityFocusedView != virtualViewId ) {
				info.addAction( AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS );
			}
			if ( this.mAccessibilityFocusedView == virtualViewId ) {
				info.addAction( AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS );
			}
			if ( NumberPicker.this.isEnabled() ) {
				info.addAction( AccessibilityNodeInfo.ACTION_CLICK );
			}

			return info;
		}

		private AccessibilityNodeInfo createAccessibiltyNodeInfoForInputText() {
			final AccessibilityNodeInfo info =
					NumberPicker.this.mInputText.createAccessibilityNodeInfo();
			info.setSource( NumberPicker.this,
					AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INPUT );
			if ( this.mAccessibilityFocusedView != AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INPUT ) {
				info.addAction( AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS );
			}
			if ( this.mAccessibilityFocusedView == AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INPUT ) {
				info.addAction( AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS );
			}
			return info;
		}

		@Override
		public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(
				final String searched, final int virtualViewId ) {
			if ( TextUtils.isEmpty( searched ) ) {
				return Collections.emptyList();
			}
			final String searchedLowerCase = searched.toLowerCase();
			final List<AccessibilityNodeInfo> result =
					new ArrayList<AccessibilityNodeInfo>();
			switch ( virtualViewId ) {
			case View.NO_ID: {
				this.findAccessibilityNodeInfosByTextInChild(
						searchedLowerCase,
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_DECREMENT,
						result );
				this.findAccessibilityNodeInfosByTextInChild(
						searchedLowerCase,
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INPUT,
						result );
				this.findAccessibilityNodeInfosByTextInChild(
						searchedLowerCase,
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INCREMENT,
						result );
				return result;
			}
			case VIRTUAL_VIEW_ID_DECREMENT:
			case VIRTUAL_VIEW_ID_INCREMENT:
			case VIRTUAL_VIEW_ID_INPUT: {
				this.findAccessibilityNodeInfosByTextInChild(
						searchedLowerCase, virtualViewId, result );
				return result;
			}
			}
			return super.findAccessibilityNodeInfosByText( searched,
					virtualViewId );
		}

		private void findAccessibilityNodeInfosByTextInChild(
				final String searchedLowerCase, final int virtualViewId,
				final List<AccessibilityNodeInfo> outResult ) {
			switch ( virtualViewId ) {
			case VIRTUAL_VIEW_ID_DECREMENT: {
				final String text = this.getVirtualDecrementButtonText();
				if ( !TextUtils.isEmpty( text )
						&& text.toString().toLowerCase().contains(
								searchedLowerCase ) ) {
					outResult.add( this.createAccessibilityNodeInfo( AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_DECREMENT ) );
				}
			}
				return;
			case VIRTUAL_VIEW_ID_INPUT: {
				final CharSequence text =
						NumberPicker.this.mInputText.getText();
				if ( !TextUtils.isEmpty( text )
						&& text.toString().toLowerCase().contains(
								searchedLowerCase ) ) {
					outResult.add( this.createAccessibilityNodeInfo( AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INPUT ) );
					return;
				}
				final CharSequence contentDesc =
						NumberPicker.this.mInputText.getText();
				if ( !TextUtils.isEmpty( contentDesc )
						&& contentDesc.toString().toLowerCase().contains(
								searchedLowerCase ) ) {
					outResult.add( this.createAccessibilityNodeInfo( AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INPUT ) );
					return;
				}
			}
				break;
			case VIRTUAL_VIEW_ID_INCREMENT: {
				final String text = this.getVirtualIncrementButtonText();
				if ( !TextUtils.isEmpty( text )
						&& text.toString().toLowerCase().contains(
								searchedLowerCase ) ) {
					outResult.add( this.createAccessibilityNodeInfo( AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INCREMENT ) );
				}
			}
				return;
			}
		}

		private String getVirtualDecrementButtonText() {
			int value = NumberPicker.this.mValue - 1;
			if ( NumberPicker.this.mWrapSelectorWheel ) {
				value = NumberPicker.this.getWrappedSelectorIndex( value );
			}
			if ( value >= NumberPicker.this.mMinValue ) {
				return ( NumberPicker.this.mDisplayedValues == null ) ? NumberPicker.this.formatNumber( value )
						: NumberPicker.this.mDisplayedValues[ value
								- NumberPicker.this.mMinValue ];
			}
			return null;
		}

		private String getVirtualIncrementButtonText() {
			int value = NumberPicker.this.mValue + 1;
			if ( NumberPicker.this.mWrapSelectorWheel ) {
				value = NumberPicker.this.getWrappedSelectorIndex( value );
			}
			if ( value <= NumberPicker.this.mMaxValue ) {
				return ( NumberPicker.this.mDisplayedValues == null ) ? NumberPicker.this.formatNumber( value )
						: NumberPicker.this.mDisplayedValues[ value
								- NumberPicker.this.mMinValue ];
			}
			return null;
		}

		private boolean hasVirtualDecrementButton() {
			return NumberPicker.this.getWrapSelectorWheel()
					|| ( NumberPicker.this.getValue() > NumberPicker.this.getMinValue() );
		}

		private boolean hasVirtualIncrementButton() {
			return NumberPicker.this.getWrapSelectorWheel()
					|| ( NumberPicker.this.getValue() < NumberPicker.this.getMaxValue() );
		}

		@Override
		public boolean performAction( final int virtualViewId,
				final int action, final Bundle arguments ) {
			switch ( virtualViewId ) {
			case View.NO_ID: {
				switch ( action ) {
				case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: {
					if ( this.mAccessibilityFocusedView != virtualViewId ) {
						this.mAccessibilityFocusedView = virtualViewId;
						// requestAccessibilityFocus();
						NumberPicker.this.performAccessibilityAction(
								AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
								null );
						return true;
					}
				}
					return false;
				case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS: {
					if ( this.mAccessibilityFocusedView == virtualViewId ) {
						this.mAccessibilityFocusedView =
								AccessibilityNodeProviderImpl.UNDEFINED;
						// clearAccessibilityFocus();
						NumberPicker.this.performAccessibilityAction(
								AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
								null );
						return true;
					}
					return false;
				}
				case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
					if ( NumberPicker.this.isEnabled()
							&& ( NumberPicker.this.getWrapSelectorWheel() || ( NumberPicker.this.getValue() < NumberPicker.this.getMaxValue() ) ) ) {
						NumberPicker.this.changeValueByOne( true );
						return true;
					}
				}
					return false;
				case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
					if ( NumberPicker.this.isEnabled()
							&& ( NumberPicker.this.getWrapSelectorWheel() || ( NumberPicker.this.getValue() > NumberPicker.this.getMinValue() ) ) ) {
						NumberPicker.this.changeValueByOne( false );
						return true;
					}
				}
					return false;
				}
			}
				break;
			case VIRTUAL_VIEW_ID_INPUT: {
				switch ( action ) {
				case AccessibilityNodeInfo.ACTION_FOCUS: {
					if ( NumberPicker.this.isEnabled()
							&& !NumberPicker.this.mInputText.isFocused() ) {
						return NumberPicker.this.mInputText.requestFocus();
					}
				}
					break;
				case AccessibilityNodeInfo.ACTION_CLEAR_FOCUS: {
					if ( NumberPicker.this.isEnabled()
							&& NumberPicker.this.mInputText.isFocused() ) {
						NumberPicker.this.mInputText.clearFocus();
						return true;
					}
					return false;
				}
				case AccessibilityNodeInfo.ACTION_CLICK: {
					if ( NumberPicker.this.isEnabled() ) {
						NumberPicker.this.showSoftInput();
						return true;
					}
					return false;
				}
				case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: {
					if ( this.mAccessibilityFocusedView != virtualViewId ) {
						this.mAccessibilityFocusedView = virtualViewId;
						this.sendAccessibilityEventForVirtualView(
								virtualViewId,
								AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED );
						NumberPicker.this.mInputText.invalidate();
						return true;
					}
				}
					return false;
				case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS: {
					if ( this.mAccessibilityFocusedView == virtualViewId ) {
						this.mAccessibilityFocusedView =
								AccessibilityNodeProviderImpl.UNDEFINED;
						this.sendAccessibilityEventForVirtualView(
								virtualViewId,
								AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED );
						NumberPicker.this.mInputText.invalidate();
						return true;
					}
				}
					return false;
				default: {
					return NumberPicker.this.mInputText.performAccessibilityAction(
							action, arguments );
				}
				}
			}
				return false;
			case VIRTUAL_VIEW_ID_INCREMENT: {
				switch ( action ) {
				case AccessibilityNodeInfo.ACTION_CLICK: {
					if ( NumberPicker.this.isEnabled() ) {
						NumberPicker.this.changeValueByOne( true );
						this.sendAccessibilityEventForVirtualView(
								virtualViewId,
								AccessibilityEvent.TYPE_VIEW_CLICKED );
						return true;
					}
				}
					return false;
				case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: {
					if ( this.mAccessibilityFocusedView != virtualViewId ) {
						this.mAccessibilityFocusedView = virtualViewId;
						this.sendAccessibilityEventForVirtualView(
								virtualViewId,
								AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED );
						NumberPicker.this.invalidate(
								0,
								NumberPicker.this.mBottomSelectionDividerBottom,
								NumberPicker.this.getRight(),
								NumberPicker.this.getBottom() );
						return true;
					}
				}
					return false;
				case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS: {
					if ( this.mAccessibilityFocusedView == virtualViewId ) {
						this.mAccessibilityFocusedView =
								AccessibilityNodeProviderImpl.UNDEFINED;
						this.sendAccessibilityEventForVirtualView(
								virtualViewId,
								AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED );
						NumberPicker.this.invalidate(
								0,
								NumberPicker.this.mBottomSelectionDividerBottom,
								NumberPicker.this.getRight(),
								NumberPicker.this.getBottom() );
						return true;
					}
				}
					return false;
				}
			}
				return false;
			case VIRTUAL_VIEW_ID_DECREMENT: {
				switch ( action ) {
				case AccessibilityNodeInfo.ACTION_CLICK: {
					if ( NumberPicker.this.isEnabled() ) {
						final boolean increment =
								( virtualViewId == AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INCREMENT );
						NumberPicker.this.changeValueByOne( increment );
						this.sendAccessibilityEventForVirtualView(
								virtualViewId,
								AccessibilityEvent.TYPE_VIEW_CLICKED );
						return true;
					}
				}
					return false;
				case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: {
					if ( this.mAccessibilityFocusedView != virtualViewId ) {
						this.mAccessibilityFocusedView = virtualViewId;
						this.sendAccessibilityEventForVirtualView(
								virtualViewId,
								AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED );
						NumberPicker.this.invalidate( 0, 0,
								NumberPicker.this.getRight(),
								NumberPicker.this.mTopSelectionDividerTop );
						return true;
					}
				}
					return false;
				case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS: {
					if ( this.mAccessibilityFocusedView == virtualViewId ) {
						this.mAccessibilityFocusedView =
								AccessibilityNodeProviderImpl.UNDEFINED;
						this.sendAccessibilityEventForVirtualView(
								virtualViewId,
								AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED );
						NumberPicker.this.invalidate( 0, 0,
								NumberPicker.this.getRight(),
								NumberPicker.this.mTopSelectionDividerTop );
						return true;
					}
				}
					return false;
				}
			}
				return false;
			}
			return super.performAction( virtualViewId, action, arguments );
		}

		private void sendAccessibilityEventForVirtualButton(
				final int virtualViewId, final int eventType, final String text ) {
			if ( ( (AccessibilityManager) NumberPicker.this.getContext().getSystemService(
					Context.ACCESSIBILITY_SERVICE ) ).isEnabled() ) {
				final AccessibilityEvent event =
						AccessibilityEvent.obtain( eventType );
				event.setClassName( Button.class.getName() );
				event.setPackageName( NumberPicker.this.getContext().getPackageName() );
				event.getText().add( text );
				event.setEnabled( NumberPicker.this.isEnabled() );
				event.setSource( NumberPicker.this, virtualViewId );
				NumberPicker.this.requestSendAccessibilityEvent(
						NumberPicker.this, event );
			}
		}

		private void sendAccessibilityEventForVirtualText( final int eventType ) {
			if ( ( (AccessibilityManager) NumberPicker.this.getContext().getSystemService(
					Context.ACCESSIBILITY_SERVICE ) ).isEnabled() ) {
				final AccessibilityEvent event =
						AccessibilityEvent.obtain( eventType );
				NumberPicker.this.mInputText.onInitializeAccessibilityEvent( event );
				NumberPicker.this.mInputText.onPopulateAccessibilityEvent( event );
				event.setSource( NumberPicker.this,
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INPUT );
				NumberPicker.this.requestSendAccessibilityEvent(
						NumberPicker.this, event );
			}
		}

		public void sendAccessibilityEventForVirtualView(
				final int virtualViewId, final int eventType ) {
			switch ( virtualViewId ) {
			case VIRTUAL_VIEW_ID_DECREMENT: {
				if ( this.hasVirtualDecrementButton() ) {
					this.sendAccessibilityEventForVirtualButton( virtualViewId,
							eventType, this.getVirtualDecrementButtonText() );
				}
			}
				break;
			case VIRTUAL_VIEW_ID_INPUT: {
				this.sendAccessibilityEventForVirtualText( eventType );
			}
				break;
			case VIRTUAL_VIEW_ID_INCREMENT: {
				if ( this.hasVirtualIncrementButton() ) {
					this.sendAccessibilityEventForVirtualButton( virtualViewId,
							eventType, this.getVirtualIncrementButtonText() );
				}
			}
				break;
			}
		}
	}

	/**
	 * Command for beginning soft input on long press.
	 */
	class BeginSoftInputOnLongPressCommand implements Runnable {

		@Override
		public void run() {
			NumberPicker.this.showSoftInput();
			NumberPicker.this.mIngonreMoveEvents = true;
		}
	}

	/**
	 * Command for changing the current value from a long press by one.
	 */
	class ChangeCurrentByOneFromLongPressCommand implements Runnable {
		private boolean mIncrement;

		@Override
		public void run() {
			NumberPicker.this.changeValueByOne( this.mIncrement );
			NumberPicker.this.postDelayed( this,
					NumberPicker.this.mLongPressUpdateInterval );
		}

		private void setStep( final boolean increment ) {
			this.mIncrement = increment;
		}
	}

	/**
	 * @hide
	 */
	public static class CustomEditText extends EditText {
		public CustomEditText( final Context context, final AttributeSet attrs ) {
			super( context, attrs );
		}

		@Override
		public void onEditorAction( final int actionCode ) {
			super.onEditorAction( actionCode );

			if ( actionCode == EditorInfo.IME_ACTION_DONE ) {
				this.clearFocus();
			}
		}
	}

	/**
	 * Interface used to format current value into a string for presentation.
	 */
	public interface Formatter {

		/**
		 * Formats a string representation of the current value.
		 * 
		 * @param value
		 *            The currently selected value.
		 * @return A formatted string representation.
		 */
		public String format( int value );
	}

	/**
	 * Filter for accepting only valid indices or prefixes of the string
	 * representation of valid indices.
	 */
	class InputTextFilter extends NumberKeyListener {
		@Override
		public CharSequence filter( final CharSequence source, final int start,
				final int end, final Spanned dest, final int dstart,
				final int dend ) {
			if ( NumberPicker.this.mDisplayedValues == null ) {
				CharSequence filtered =
						super.filter( source, start, end, dest, dstart, dend );

				if ( filtered == null ) {
					filtered = source.subSequence( start, end );
				}

				final String result =
						String.valueOf( dest.subSequence( 0, dstart ) )
								+ filtered
								+ dest.subSequence( dend, dest.length() );

				if ( "".equals( result ) ) {
					return result;
				}

				final int val = NumberPicker.this.getSelectedPos( result );

				/*
				 * Ensure the user can't type in a value greater than the max
				 * allowed. We have to allow less than min as the user might
				 * want to delete some numbers and then type a new number.
				 */
				if ( val > NumberPicker.this.mMaxValue ) {
					return "";
				} else {
					return filtered;
				}
			} else {
				final CharSequence filtered =
						String.valueOf( source.subSequence( start, end ) );

				if ( TextUtils.isEmpty( filtered ) ) {
					return "";
				}

				final String result =
						String.valueOf( dest.subSequence( 0, dstart ) )
								+ filtered
								+ dest.subSequence( dend, dest.length() );
				final String str = String.valueOf( result ).toLowerCase();
				CharSequence bestMatch = "";

				for ( final String val : NumberPicker.this.mDisplayedValues ) {
					final String valLowerCase = val.toLowerCase();

					if ( valLowerCase.equals( str ) ) {
						NumberPicker.this.postSetSelectionCommand(
								result.length(), val.length() );
						bestMatch = val.subSequence( dstart, val.length() );

						break;
					} else {
						if ( valLowerCase.startsWith( str )
								&& bestMatch.equals( "" ) ) {
							NumberPicker.this.postSetSelectionCommand(
									result.length(), val.length() );
							bestMatch = val.subSequence( dstart, val.length() );
						}
					}
				}

				return bestMatch;
			}
		}

		@Override
		protected char[] getAcceptedChars() {
			return NumberPicker.DIGIT_CHARACTERS;
		}

		// XXX This doesn't allow for range limits when controlled by a
		// soft input method!
		@Override
		public int getInputType() {
			return InputType.TYPE_CLASS_TEXT;
		}
	}

	/**
	 * Interface to listen for the picker scroll state.
	 */
	public interface OnScrollListener {

		/**
		 * The view is not scrolling.
		 */
		public static int SCROLL_STATE_IDLE = 0;

		/**
		 * The user is scrolling using touch, and his finger is still on the
		 * screen.
		 */
		public static int SCROLL_STATE_TOUCH_SCROLL = 1;

		/**
		 * The user had previously been scrolling using touch and performed a
		 * fling.
		 */
		public static int SCROLL_STATE_FLING = 2;

		/**
		 * Callback invoked while the number picker scroll state has changed.
		 * 
		 * @param view
		 *            The view whose scroll state is being reported.
		 * @param scrollState
		 *            The current scroll state. One of
		 *            {@link #SCROLL_STATE_IDLE},
		 *            {@link #SCROLL_STATE_TOUCH_SCROLL} or
		 *            {@link #SCROLL_STATE_IDLE}.
		 */
		public void onScrollStateChange( NumberPicker view, int scrollState );
	}

	/**
	 * Interface to listen for changes of the current value.
	 */
	public interface OnValueChangeListener {

		/**
		 * Called upon a change of the current value.
		 * 
		 * @param picker
		 *            The NumberPicker associated with this listener.
		 * @param oldVal
		 *            The previous value.
		 * @param newVal
		 *            The new value.
		 */
		void onValueChange( NumberPicker picker, int oldVal, int newVal );
	}

	class PressedStateHelper implements Runnable {
		public static final int BUTTON_INCREMENT = 1;
		public static final int BUTTON_DECREMENT = 2;

		private final int MODE_PRESS = 1;
		private final int MODE_TAPPED = 2;

		private int mManagedButton;
		private int mMode;

		public void buttonPressDelayed( final int button ) {
			this.cancel();
			this.mMode = this.MODE_PRESS;
			this.mManagedButton = button;
			NumberPicker.this.postDelayed( this,
					ViewConfiguration.getTapTimeout() );
		}

		public void buttonTapped( final int button ) {
			this.cancel();
			this.mMode = this.MODE_TAPPED;
			this.mManagedButton = button;
			NumberPicker.this.post( this );
		}

		public void cancel() {
			this.mMode = 0;
			this.mManagedButton = 0;
			NumberPicker.this.removeCallbacks( this );
			if ( NumberPicker.this.mIncrementVirtualButtonPressed ) {
				NumberPicker.this.mIncrementVirtualButtonPressed = false;
				NumberPicker.this.invalidate( 0,
						NumberPicker.this.mBottomSelectionDividerBottom,
						NumberPicker.this.getRight(),
						NumberPicker.this.getBottom() );
			}
			NumberPicker.this.mDecrementVirtualButtonPressed = false;
			if ( NumberPicker.this.mDecrementVirtualButtonPressed ) {
				NumberPicker.this.invalidate( 0, 0,
						NumberPicker.this.getRight(),
						NumberPicker.this.mTopSelectionDividerTop );
			}
		}

		@Override
		public void run() {
			switch ( this.mMode ) {
			case MODE_PRESS: {
				switch ( this.mManagedButton ) {
				case BUTTON_INCREMENT: {
					NumberPicker.this.mIncrementVirtualButtonPressed = true;
					NumberPicker.this.invalidate( 0,
							NumberPicker.this.mBottomSelectionDividerBottom,
							NumberPicker.this.getRight(),
							NumberPicker.this.getBottom() );
				}
					break;
				case BUTTON_DECREMENT: {
					NumberPicker.this.mDecrementVirtualButtonPressed = true;
					NumberPicker.this.invalidate( 0, 0,
							NumberPicker.this.getRight(),
							NumberPicker.this.mTopSelectionDividerTop );
				}
				}
			}
				break;
			case MODE_TAPPED: {
				switch ( this.mManagedButton ) {
				case BUTTON_INCREMENT: {
					if ( !NumberPicker.this.mIncrementVirtualButtonPressed ) {
						NumberPicker.this.postDelayed( this,
								ViewConfiguration.getPressedStateDuration() );
					}
					NumberPicker.this.mIncrementVirtualButtonPressed ^= true;
					NumberPicker.this.invalidate( 0,
							NumberPicker.this.mBottomSelectionDividerBottom,
							NumberPicker.this.getRight(),
							NumberPicker.this.getBottom() );
				}
					break;
				case BUTTON_DECREMENT: {
					if ( !NumberPicker.this.mDecrementVirtualButtonPressed ) {
						NumberPicker.this.postDelayed( this,
								ViewConfiguration.getPressedStateDuration() );
					}
					NumberPicker.this.mDecrementVirtualButtonPressed ^= true;
					NumberPicker.this.invalidate( 0, 0,
							NumberPicker.this.getRight(),
							NumberPicker.this.mTopSelectionDividerTop );
				}
				}
			}
				break;
			}
		}
	}

	/**
	 * Command for setting the input text selection.
	 */
	class SetSelectionCommand implements Runnable {
		private int mSelectionStart;

		private int mSelectionEnd;

		@Override
		public void run() {
			NumberPicker.this.mInputText.setSelection( this.mSelectionStart,
					this.mSelectionEnd );
		}
	}

	class SupportAccessibilityNodeProvider {

		AccessibilityNodeProviderImpl mProvider;

		private SupportAccessibilityNodeProvider() {
			if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ) {
				this.mProvider = new AccessibilityNodeProviderImpl();
			}
		}

		@SuppressLint( "NewApi" )
		public boolean performAction( final int virtualViewId,
				final int action, final Bundle arguments ) {
			if ( this.mProvider != null ) {
				return this.mProvider.performAction( virtualViewId, action,
						arguments );
			}

			return false;
		}

		public void sendAccessibilityEventForVirtualView(
				final int virtualViewId, final int eventType ) {
			if ( this.mProvider != null ) {
				this.mProvider.sendAccessibilityEventForVirtualView(
						virtualViewId, eventType );
			}
		}
	}

	/**
	 * Use a custom NumberPicker formatting callback to use two-digit minutes
	 * strings like "01". Keeping a static formatter etc. is the most efficient
	 * way to do this; it avoids creating temporary objects on every call to
	 * format().
	 */
	private static class TwoDigitFormatter implements NumberPicker.Formatter {
		private static char getZeroDigit( final Locale locale ) {
			// return LocaleData.get(locale).zeroDigit;
			return new DecimalFormatSymbols( locale ).getZeroDigit();
		}

		final StringBuilder mBuilder = new StringBuilder();
		char mZeroDigit;

		java.util.Formatter mFmt;

		final Object[] mArgs = new Object[ 1 ];

		TwoDigitFormatter() {
			final Locale locale = Locale.getDefault();
			this.init( locale );
		}

		private java.util.Formatter createFormatter( final Locale locale ) {
			return new java.util.Formatter( this.mBuilder, locale );
		}

		@Override
		public String format( final int value ) {
			final Locale currentLocale = Locale.getDefault();
			if ( this.mZeroDigit != TwoDigitFormatter.getZeroDigit( currentLocale ) ) {
				this.init( currentLocale );
			}
			this.mArgs[ 0 ] = value;
			this.mBuilder.delete( 0, this.mBuilder.length() );
			this.mFmt.format( "%02d", this.mArgs );
			return this.mFmt.toString();
		}

		private void init( final Locale locale ) {
			this.mFmt = this.createFormatter( locale );
			this.mZeroDigit = TwoDigitFormatter.getZeroDigit( locale );
		}
	}

	/**
	 * The number of items show in the selector wheel.
	 */
	private static final int SELECTOR_WHEEL_ITEM_COUNT = 3;

	/**
	 * The default update interval during long press.
	 */
	private static final long DEFAULT_LONG_PRESS_UPDATE_INTERVAL = 300;

	/**
	 * The index of the middle selector item.
	 */
	private static final int SELECTOR_MIDDLE_ITEM_INDEX =
			NumberPicker.SELECTOR_WHEEL_ITEM_COUNT / 2;

	/**
	 * The coefficient by which to adjust (divide) the max fling velocity.
	 */
	private static final int SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT = 8;

	/**
	 * The the duration for adjusting the selector wheel.
	 */
	private static final int SELECTOR_ADJUSTMENT_DURATION_MILLIS = 800;

	/**
	 * The duration of scrolling while snapping to a given position.
	 */
	private static final int SNAP_SCROLL_DURATION = 300;

	/**
	 * The strength of fading in the top and bottom while drawing the selector.
	 */
	private static final float TOP_AND_BOTTOM_FADING_EDGE_STRENGTH = 0.9f;

	/**
	 * The default unscaled height of the selection divider.
	 */
	private static final int UNSCALED_DEFAULT_SELECTION_DIVIDER_HEIGHT = 2;

	/**
	 * The default unscaled distance between the selection dividers.
	 */
	private static final int UNSCALED_DEFAULT_SELECTION_DIVIDERS_DISTANCE = 48;

	/**
	 * The resource id for the default layout.
	 */
	private static final int DEFAULT_LAYOUT_RESOURCE_ID = 0;

	/**
	 * Constant for unspecified size.
	 */
	private static final int SIZE_UNSPECIFIED = -1;

	private static final TwoDigitFormatter sTwoDigitFormatter =
			new TwoDigitFormatter();

	static private String formatNumberWithLocale( final int value ) {
		return String.format( Locale.getDefault(), "%d", value );
	}

	/**
	 * @hide
	 */
	public static final Formatter getTwoDigitFormatter() {
		return NumberPicker.sTwoDigitFormatter;
	}

	/**
	 * The increment button.
	 */
	private final ImageButton mIncrementButton;

	/**
	 * The decrement button.
	 */
	private final ImageButton mDecrementButton;

	/**
	 * The text for showing the current value.
	 */
	private final EditText mInputText;

	/**
	 * The distance between the two selection dividers.
	 */
	private final int mSelectionDividersDistance;

	/**
	 * The min height of this widget.
	 */
	private final int mMinHeight;

	/**
	 * The max height of this widget.
	 */
	private final int mMaxHeight;

	/**
	 * The max width of this widget.
	 */
	private final int mMinWidth;

	/**
	 * The max width of this widget.
	 */
	private int mMaxWidth;

	/**
	 * The IME options requested.
	 */
	private int mImeOptions = EditorInfo.IME_ACTION_DONE;

	/**
	 * Flag whether to compute the max width.
	 */
	private final boolean mComputeMaxWidth;

	/**
	 * The height of the text.
	 */
	private final int mTextSize;

	/**
	 * The height of the gap between text elements if the selector wheel.
	 */
	private int mSelectorTextGapHeight;

	/**
	 * The values to be displayed instead the indices.
	 */
	private String[] mDisplayedValues;

	/**
	 * Lower value of the range of numbers allowed for the NumberPicker
	 */
	private int mMinValue;

	/**
	 * Upper value of the range of numbers allowed for the NumberPicker
	 */
	private int mMaxValue;

	/**
	 * Current value of this NumberPicker
	 */
	private int mValue;

	/**
	 * Listener to be notified upon current value change.
	 */
	private OnValueChangeListener mOnValueChangeListener;

	/**
	 * Listener to be notified upon scroll state change.
	 */
	private OnScrollListener mOnScrollListener;

	/**
	 * Formatter for for displaying the current value.
	 */
	private Formatter mFormatter;

	/**
	 * The speed for updating the value form long press.
	 */
	private long mLongPressUpdateInterval =
			NumberPicker.DEFAULT_LONG_PRESS_UPDATE_INTERVAL;

	/**
	 * Cache for the string representation of selector indices.
	 */
	private final SparseArray<String> mSelectorIndexToStringCache =
			new SparseArray<String>();

	/**
	 * The selector indices whose value are show by the selector.
	 */
	private final int[] mSelectorIndices =
			new int[ NumberPicker.SELECTOR_WHEEL_ITEM_COUNT ];

	/**
	 * The {@link Paint} for drawing the selector.
	 */
	private final Paint mSelectorWheelPaint;

	/**
	 * The {@link Drawable} for pressed virtual (increment/decrement) buttons.
	 */
	private final Drawable mVirtualButtonPressedDrawable;

	/**
	 * The height of a selector element (text + gap).
	 */
	private int mSelectorElementHeight;

	/**
	 * The initial offset of the scroll selector.
	 */
	private int mInitialScrollOffset = Integer.MIN_VALUE;

	/**
	 * The current offset of the scroll selector.
	 */
	private int mCurrentScrollOffset;

	/**
	 * The {@link Scroller} responsible for flinging the selector.
	 */
	private final Scroller mFlingScroller;

	/**
	 * The {@link Scroller} responsible for adjusting the selector.
	 */
	private final Scroller mAdjustScroller;

	/**
	 * The previous Y coordinate while scrolling the selector.
	 */
	private int mPreviousScrollerY;

	/**
	 * Handle to the reusable command for setting the input text selection.
	 */
	private SetSelectionCommand mSetSelectionCommand;

	/**
	 * Handle to the reusable command for changing the current value from long
	 * press by one.
	 */
	private ChangeCurrentByOneFromLongPressCommand mChangeCurrentByOneFromLongPressCommand;

	/**
	 * Command for beginning an edit of the current value via IME on long press.
	 */
	private BeginSoftInputOnLongPressCommand mBeginSoftInputOnLongPressCommand;

	/**
	 * The Y position of the last down event.
	 */
	private float mLastDownEventY;

	/**
	 * The time of the last down event.
	 */
	private long mLastDownEventTime;

	/**
	 * The Y position of the last down or move event.
	 */
	private float mLastDownOrMoveEventY;

	/**
	 * Determines speed during touch scrolling.
	 */
	private VelocityTracker mVelocityTracker;

	/**
	 * @see ViewConfiguration#getScaledTouchSlop()
	 */
	private int mTouchSlop;

	/**
	 * @see ViewConfiguration#getScaledMinimumFlingVelocity()
	 */
	private int mMinimumFlingVelocity;

	/**
	 * @see ViewConfiguration#getScaledMaximumFlingVelocity()
	 */
	private int mMaximumFlingVelocity;

	/**
	 * Flag whether the selector should wrap around.
	 */
	private boolean mWrapSelectorWheel;

	/**
	 * The back ground color used to optimize scroller fading.
	 */
	private final int mSolidColor;

	/**
	 * Flag whether this widget has a selector wheel.
	 */
	private final boolean mHasSelectorWheel;

	/**
	 * Divider for showing item to be selected while scrolling
	 */
	private final Drawable mSelectionDivider;

	/**
	 * The height of the selection divider.
	 */
	private final int mSelectionDividerHeight;

	/**
	 * The current scroll state of the number picker.
	 */
	private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;

	/**
	 * Flag whether to ignore move events - we ignore such when we show in IME
	 * to prevent the content from scrolling.
	 */
	private boolean mIngonreMoveEvents;

	/**
	 * Flag whether to show soft input on tap.
	 */
	private boolean mShowSoftInputOnTap;

	/**
	 * The top of the top selection divider.
	 */
	private int mTopSelectionDividerTop;

	/**
	 * The bottom of the bottom selection divider.
	 */
	private int mBottomSelectionDividerBottom;

	/**
	 * The virtual id of the last hovered child.
	 */
	private int mLastHoveredChildVirtualViewId;

	/**
	 * Whether the increment virtual button is pressed.
	 */
	private boolean mIncrementVirtualButtonPressed;

	/**
	 * Whether the decrement virtual button is pressed.
	 */
	private boolean mDecrementVirtualButtonPressed;

	/**
	 * Provider to report to clients the semantic structure of this widget.
	 */
	private SupportAccessibilityNodeProvider mAccessibilityNodeProvider;

	/**
	 * Helper class for managing pressed state of the virtual buttons.
	 */
	private final PressedStateHelper mPressedStateHelper;

	/**
	 * The keycode of the last handled DPAD down event.
	 */
	private int mLastHandledDownDpadKeyCode = -1;

	/**
	 * The numbers accepted by the input text's {@link Filter}
	 */
	private static final char[] DIGIT_CHARACTERS = new char[] {
			// Latin digits are the common case
			'0', '1', '2', '3', '4', '5', '6', '7', '8',
			'9',
			// Arabic-Indic
			'\u0660', '\u0661', '\u0662', '\u0663', '\u0664', '\u0665',
			'\u0666', '\u0667', '\u0668', '\u0669',
			// Extended Arabic-Indic
			'\u06f0', '\u06f1', '\u06f2', '\u06f3', '\u06f4', '\u06f5',
			'\u06f6', '\u06f7', '\u06f8', '\u06f9' };

	/**
	 * Utility to reconcile a desired size and state, with constraints imposed
	 * by a MeasureSpec. Will take the desired size, unless a different size is
	 * imposed by the constraints. The returned value is a compound integer,
	 * with the resolved size in the {@link #MEASURED_SIZE_MASK} bits and
	 * optionally the bit {@link #MEASURED_STATE_TOO_SMALL} set if the resulting
	 * size is smaller than the size the view wants to be.
	 * 
	 * @param size
	 *            How big the view wants to be
	 * @param measureSpec
	 *            Constraints imposed by the parent
	 * @return Size information bit mask as defined by
	 *         {@link #MEASURED_SIZE_MASK} and {@link #MEASURED_STATE_TOO_SMALL}
	 *         .
	 */
	public static int resolveSizeAndState( final int size,
			final int measureSpec, final int childMeasuredState ) {
		int result = size;
		final int specMode = MeasureSpec.getMode( measureSpec );
		final int specSize = MeasureSpec.getSize( measureSpec );
		switch ( specMode ) {
		case MeasureSpec.UNSPECIFIED:
			result = size;
			break;
		case MeasureSpec.AT_MOST:
			if ( specSize < size ) {
				result = specSize | View.MEASURED_STATE_TOO_SMALL;
			} else {
				result = size;
			}
			break;
		case MeasureSpec.EXACTLY:
			result = specSize;
			break;
		}
		return result | ( childMeasuredState & View.MEASURED_STATE_MASK );
	}

	/**
	 * Create a new number picker.
	 * 
	 * @param context
	 *            The application environment.
	 */
	public NumberPicker( final Context context ) {
		this( context, null );
	}

	/**
	 * Create a new number picker.
	 * 
	 * @param context
	 *            The application environment.
	 * @param attrs
	 *            A collection of attributes.
	 */
	public NumberPicker( final Context context, final AttributeSet attrs ) {
		this( context, attrs, R.attr.numberPickerStyle );
	}

	/**
	 * Create a new number picker
	 * 
	 * @param context
	 *            the application environment.
	 * @param attrs
	 *            a collection of attributes.
	 * @param defStyle
	 *            The default style to apply to this view.
	 */
	@SuppressLint( "NewApi" )
	public NumberPicker( final Context context, final AttributeSet attrs,
			final int defStyle ) {
		super( context, attrs );

		// process style attributes
		final TypedArray attributesArray =
				context.obtainStyledAttributes( attrs,
						R.styleable.NumberPicker, defStyle, 0 );
		final int layoutResId =
				attributesArray.getResourceId(
						R.styleable.NumberPicker_internalLayout,
						NumberPicker.DEFAULT_LAYOUT_RESOURCE_ID );

		this.mHasSelectorWheel =
				( layoutResId != NumberPicker.DEFAULT_LAYOUT_RESOURCE_ID );

		this.mSolidColor =
				attributesArray.getColor( R.styleable.NumberPicker_solidColor,
						0 );

		this.mSelectionDivider =
				attributesArray.getDrawable( R.styleable.NumberPicker_selectionDivider );

		final int defSelectionDividerHeight =
				(int) TypedValue.applyDimension( TypedValue.COMPLEX_UNIT_DIP,
						NumberPicker.UNSCALED_DEFAULT_SELECTION_DIVIDER_HEIGHT,
						this.getResources().getDisplayMetrics() );
		this.mSelectionDividerHeight =
				attributesArray.getDimensionPixelSize(
						R.styleable.NumberPicker_selectionDividerHeight,
						defSelectionDividerHeight );

		final int defSelectionDividerDistance =
				(int) TypedValue.applyDimension(
						TypedValue.COMPLEX_UNIT_DIP,
						NumberPicker.UNSCALED_DEFAULT_SELECTION_DIVIDERS_DISTANCE,
						this.getResources().getDisplayMetrics() );
		this.mSelectionDividersDistance =
				attributesArray.getDimensionPixelSize(
						R.styleable.NumberPicker_selectionDividersDistance,
						defSelectionDividerDistance );

		this.mMinHeight =
				attributesArray.getDimensionPixelSize(
						R.styleable.NumberPicker_internalMinHeight,
						NumberPicker.SIZE_UNSPECIFIED );

		this.mMaxHeight =
				attributesArray.getDimensionPixelSize(
						R.styleable.NumberPicker_internalMaxHeight,
						NumberPicker.SIZE_UNSPECIFIED );
		if ( ( this.mMinHeight != NumberPicker.SIZE_UNSPECIFIED )
				&& ( this.mMaxHeight != NumberPicker.SIZE_UNSPECIFIED )
				&& ( this.mMinHeight > this.mMaxHeight ) ) {
			throw new IllegalArgumentException( "minHeight > maxHeight" );
		}

		this.mMinWidth =
				attributesArray.getDimensionPixelSize(
						R.styleable.NumberPicker_internalMinWidth,
						NumberPicker.SIZE_UNSPECIFIED );

		this.mMaxWidth =
				attributesArray.getDimensionPixelSize(
						R.styleable.NumberPicker_internalMaxWidth,
						NumberPicker.SIZE_UNSPECIFIED );
		if ( ( this.mMinWidth != NumberPicker.SIZE_UNSPECIFIED )
				&& ( this.mMaxWidth != NumberPicker.SIZE_UNSPECIFIED )
				&& ( this.mMinWidth > this.mMaxWidth ) ) {
			throw new IllegalArgumentException( "minWidth > maxWidth" );
		}

		this.mComputeMaxWidth =
				( this.mMaxWidth == NumberPicker.SIZE_UNSPECIFIED );

		this.mVirtualButtonPressedDrawable =
				attributesArray.getDrawable( R.styleable.NumberPicker_virtualButtonPressedDrawable );

		this.mImeOptions =
				attributesArray.getInt(
						R.styleable.NumberPicker_android_imeOptions,
						this.mImeOptions ); 

		attributesArray.recycle();

		this.mPressedStateHelper = new PressedStateHelper();

		// By default Linearlayout that we extend is not drawn. This is
		// its draw() method is not called but dispatchDraw() is called
		// directly (see ViewGroup.drawChild()). However, this class uses
		// the fading edge effect implemented by View and we need our
		// draw() method to be called. Therefore, we declare we will draw.
		this.setWillNotDraw( !this.mHasSelectorWheel );

		final LayoutInflater inflater =
				(LayoutInflater) this.getContext().getSystemService(
						Context.LAYOUT_INFLATER_SERVICE );
		inflater.inflate( layoutResId, this, true );

		final OnClickListener onClickListener = new OnClickListener() {
			@Override
			public void onClick( final View v ) {
				NumberPicker.this.hideSoftInput();
				NumberPicker.this.mInputText.clearFocus();
				if ( v.getId() == R.id.np__increment ) {
					NumberPicker.this.changeValueByOne( true );
				} else {
					NumberPicker.this.changeValueByOne( false );
				}
			}
		};

		final OnLongClickListener onLongClickListener =
				new OnLongClickListener() {
					@Override
					public boolean onLongClick( final View v ) {
						NumberPicker.this.hideSoftInput();
						NumberPicker.this.mInputText.clearFocus();
						if ( v.getId() == R.id.np__increment ) {
							NumberPicker.this.postChangeCurrentByOneFromLongPress(
									true, 0 );
						} else {
							NumberPicker.this.postChangeCurrentByOneFromLongPress(
									false, 0 );
						}
						return true;
					}
				};

		// increment button
		if ( !this.mHasSelectorWheel ) {
			this.mIncrementButton =
					(ImageButton) this.findViewById( R.id.np__increment );
			this.mIncrementButton.setOnClickListener( onClickListener );
			this.mIncrementButton.setOnLongClickListener( onLongClickListener );
		} else {
			this.mIncrementButton = null;
		}

		// decrement button
		if ( !this.mHasSelectorWheel ) {
			this.mDecrementButton =
					(ImageButton) this.findViewById( R.id.np__decrement );
			this.mDecrementButton.setOnClickListener( onClickListener );
			this.mDecrementButton.setOnLongClickListener( onLongClickListener );
		} else {
			this.mDecrementButton = null;
		}

		// input text
		this.mInputText =
				(EditText) this.findViewById( R.id.np__numberpicker_input );
		this.mInputText.setOnFocusChangeListener( new OnFocusChangeListener() {
			@Override
			public void onFocusChange( final View v, final boolean hasFocus ) {
				if ( hasFocus ) {
					NumberPicker.this.mInputText.selectAll();
				} else {
					NumberPicker.this.mInputText.setSelection( 0, 0 );
					NumberPicker.this.validateInputTextView( v );
				}
			}
		} );
		this.mInputText.setFilters( new InputFilter[] { new InputTextFilter() } );

		this.mInputText.setRawInputType( InputType.TYPE_CLASS_NUMBER );
		this.mInputText.setImeOptions( this.mImeOptions );

		// initialize constants
		final ViewConfiguration configuration = ViewConfiguration.get( context );
		this.mTouchSlop = configuration.getScaledTouchSlop();
		this.mMinimumFlingVelocity =
				configuration.getScaledMinimumFlingVelocity();
		this.mMaximumFlingVelocity =
				configuration.getScaledMaximumFlingVelocity()
						/ NumberPicker.SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT;
		this.mTextSize = (int) this.mInputText.getTextSize();

		// create the selector wheel paint
		final Paint paint = new Paint();
		paint.setAntiAlias( true );
		paint.setTextAlign( Align.CENTER );
		paint.setTextSize( this.mTextSize );
		paint.setTypeface( this.mInputText.getTypeface() );
		final ColorStateList colors = this.mInputText.getTextColors();
		final int color =
				colors.getColorForState( View.ENABLED_STATE_SET, Color.WHITE );
		paint.setColor( color );
		this.mSelectorWheelPaint = paint;

		// create the fling and adjust scrollers
		this.mFlingScroller = new Scroller( this.getContext(), null, true );
		this.mAdjustScroller =
				new Scroller( this.getContext(), new DecelerateInterpolator(
						2.5f ) );

		this.updateInputTextView();

		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ) {
			// If not explicitly specified this view is important for
			// accessibility.
			if ( this.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO ) {
				this.setImportantForAccessibility( View.IMPORTANT_FOR_ACCESSIBILITY_YES );
			}
		}
	}

	/**
	 * Changes the current value by one which is increment or decrement based on
	 * the passes argument. decrement the current value.
	 * 
	 * @param increment
	 *            True to increment, false to decrement.
	 */
	private void changeValueByOne( final boolean increment ) {
		if ( this.mHasSelectorWheel ) {
			this.mInputText.setVisibility( View.INVISIBLE );
			if ( !this.moveToFinalScrollerPosition( this.mFlingScroller ) ) {
				this.moveToFinalScrollerPosition( this.mAdjustScroller );
			}
			this.mPreviousScrollerY = 0;
			if ( increment ) {
				this.mFlingScroller.startScroll( 0, 0, 0,
						-this.mSelectorElementHeight,
						NumberPicker.SNAP_SCROLL_DURATION );
			} else {
				this.mFlingScroller.startScroll( 0, 0, 0,
						this.mSelectorElementHeight,
						NumberPicker.SNAP_SCROLL_DURATION );
			}
			this.invalidate();
		} else {
			if ( increment ) {
				this.setValueInternal( this.mValue + 1, true );
			} else {
				this.setValueInternal( this.mValue - 1, true );
			}
		}
	}

	@Override
	public void computeScroll() {
		Scroller scroller = this.mFlingScroller;
		if ( scroller.isFinished() ) {
			scroller = this.mAdjustScroller;
			if ( scroller.isFinished() ) {
				return;
			}
		}
		scroller.computeScrollOffset();
		final int currentScrollerY = scroller.getCurrY();
		if ( this.mPreviousScrollerY == 0 ) {
			this.mPreviousScrollerY = scroller.getStartY();
		}
		this.scrollBy( 0, currentScrollerY - this.mPreviousScrollerY );
		this.mPreviousScrollerY = currentScrollerY;
		if ( scroller.isFinished() ) {
			this.onScrollerFinished( scroller );
		} else {
			this.invalidate();
		}
	}

	/**
	 * Decrements the <code>selectorIndices</code> whose string representations
	 * will be displayed in the selector.
	 */
	private void decrementSelectorIndices( final int[] selectorIndices ) {
		for ( int i = selectorIndices.length - 1; i > 0; i-- ) {
			selectorIndices[ i ] = selectorIndices[ i - 1 ];
		}
		int nextScrollSelectorIndex = selectorIndices[ 1 ] - 1;
		if ( this.mWrapSelectorWheel
				&& ( nextScrollSelectorIndex < this.mMinValue ) ) {
			nextScrollSelectorIndex = this.mMaxValue;
		}
		selectorIndices[ 0 ] = nextScrollSelectorIndex;
		this.ensureCachedScrollSelectorValue( nextScrollSelectorIndex );
	}

	@SuppressLint( "NewApi" )
	@Override
	protected boolean dispatchHoverEvent( final MotionEvent event ) {
		if ( !this.mHasSelectorWheel ) {
			return super.dispatchHoverEvent( event );
		}

		if ( ( (AccessibilityManager) this.getContext().getSystemService(
				Context.ACCESSIBILITY_SERVICE ) ).isEnabled() ) {
			final int eventY = (int) event.getY();
			final int hoveredVirtualViewId;
			if ( eventY < this.mTopSelectionDividerTop ) {
				hoveredVirtualViewId =
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_DECREMENT;
			} else if ( eventY > this.mBottomSelectionDividerBottom ) {
				hoveredVirtualViewId =
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INCREMENT;
			} else {
				hoveredVirtualViewId =
						AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INPUT;
			}
			final int action = event.getAction() & MotionEvent.ACTION_MASK;
			final SupportAccessibilityNodeProvider provider =
					this.getSupportAccessibilityNodeProvider();

			switch ( action ) {
			case MotionEvent.ACTION_HOVER_ENTER: {
				provider.sendAccessibilityEventForVirtualView(
						hoveredVirtualViewId,
						AccessibilityEvent.TYPE_VIEW_HOVER_ENTER );
				this.mLastHoveredChildVirtualViewId = hoveredVirtualViewId;
				provider.performAction( hoveredVirtualViewId,
						AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null );
			}
				break;
			case MotionEvent.ACTION_HOVER_MOVE: {
				if ( ( this.mLastHoveredChildVirtualViewId != hoveredVirtualViewId )
						&& ( this.mLastHoveredChildVirtualViewId != View.NO_ID ) ) {
					provider.sendAccessibilityEventForVirtualView(
							this.mLastHoveredChildVirtualViewId,
							AccessibilityEvent.TYPE_VIEW_HOVER_EXIT );
					provider.sendAccessibilityEventForVirtualView(
							hoveredVirtualViewId,
							AccessibilityEvent.TYPE_VIEW_HOVER_ENTER );
					this.mLastHoveredChildVirtualViewId = hoveredVirtualViewId;
					provider.performAction( hoveredVirtualViewId,
							AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
							null );
				}
			}
				break;
			case MotionEvent.ACTION_HOVER_EXIT: {
				provider.sendAccessibilityEventForVirtualView(
						hoveredVirtualViewId,
						AccessibilityEvent.TYPE_VIEW_HOVER_EXIT );
				this.mLastHoveredChildVirtualViewId = View.NO_ID;
			}
				break;
			}
		}
		return false;
	}

	@Override
	public boolean dispatchKeyEvent( final KeyEvent event ) {
		final int keyCode = event.getKeyCode();
		switch ( keyCode ) {
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_ENTER:
			this.removeAllCallbacks();
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
		case KeyEvent.KEYCODE_DPAD_UP:
			if ( !this.mHasSelectorWheel ) {
				break;
			}
			switch ( event.getAction() ) {
			case KeyEvent.ACTION_DOWN:
				if ( this.mWrapSelectorWheel
						|| ( keyCode == KeyEvent.KEYCODE_DPAD_DOWN ) ? this.getValue() < this.getMaxValue()
						: this.getValue() > this.getMinValue() ) {
					this.requestFocus();
					this.mLastHandledDownDpadKeyCode = keyCode;
					this.removeAllCallbacks();
					if ( this.mFlingScroller.isFinished() ) {
						this.changeValueByOne( keyCode == KeyEvent.KEYCODE_DPAD_DOWN );
					}
					return true;
				}
				break;
			case KeyEvent.ACTION_UP:
				if ( this.mLastHandledDownDpadKeyCode == keyCode ) {
					this.mLastHandledDownDpadKeyCode = -1;
					return true;
				}
				break;
			}
		}
		return super.dispatchKeyEvent( event );
	}

	@Override
	public boolean dispatchTouchEvent( final MotionEvent event ) {
		final int action = event.getAction() & MotionEvent.ACTION_MASK;
		switch ( action ) {
		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP:
			this.removeAllCallbacks();
			break;
		}
		return super.dispatchTouchEvent( event );
	}

	@Override
	public boolean dispatchTrackballEvent( final MotionEvent event ) {
		final int action = event.getAction() & MotionEvent.ACTION_MASK;
		switch ( action ) {
		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP:
			this.removeAllCallbacks();
			break;
		}
		return super.dispatchTrackballEvent( event );
	}

	/**
	 * Ensures we have a cached string representation of the given <code>
	 * selectorIndex</code> to avoid multiple instantiations of the same string.
	 */
	private void ensureCachedScrollSelectorValue( final int selectorIndex ) {
		final SparseArray<String> cache = this.mSelectorIndexToStringCache;
		String scrollSelectorValue = cache.get( selectorIndex );
		if ( scrollSelectorValue != null ) {
			return;
		}
		if ( ( selectorIndex < this.mMinValue )
				|| ( selectorIndex > this.mMaxValue ) ) {
			scrollSelectorValue = "";
		} else {
			if ( this.mDisplayedValues != null ) {
				final int displayedValueIndex = selectorIndex - this.mMinValue;
				scrollSelectorValue =
						this.mDisplayedValues[ displayedValueIndex ];
			} else {
				scrollSelectorValue = this.formatNumber( selectorIndex );
			}
		}
		cache.put( selectorIndex, scrollSelectorValue );
	}

	/**
	 * Ensures that the scroll wheel is adjusted i.e. there is no offset and the
	 * middle element is in the middle of the widget.
	 * 
	 * @return Whether an adjustment has been made.
	 */
	private boolean ensureScrollWheelAdjusted() {
		// adjust to the closest value
		int deltaY = this.mInitialScrollOffset - this.mCurrentScrollOffset;
		if ( deltaY != 0 ) {
			this.mPreviousScrollerY = 0;
			if ( Math.abs( deltaY ) > ( this.mSelectorElementHeight / 2 ) ) {
				deltaY +=
						( deltaY > 0 ) ? -this.mSelectorElementHeight
								: this.mSelectorElementHeight;
			}
			this.mAdjustScroller.startScroll( 0, 0, 0, deltaY,
					NumberPicker.SELECTOR_ADJUSTMENT_DURATION_MILLIS );
			this.invalidate();
			return true;
		}
		return false;
	}

	/**
	 * Flings the selector with the given <code>velocityY</code>.
	 */
	private void fling( final int velocityY ) {
		this.mPreviousScrollerY = 0;

		if ( velocityY > 0 ) {
			this.mFlingScroller.fling( 0, 0, 0, velocityY, 0, 0, 0,
					Integer.MAX_VALUE );
		} else {
			this.mFlingScroller.fling( 0, Integer.MAX_VALUE, 0, velocityY, 0,
					0, 0, Integer.MAX_VALUE );
		}

		this.invalidate();
	}

	private String formatNumber( final int value ) {
		return ( this.mFormatter != null ) ? this.mFormatter.format( value )
				: NumberPicker.formatNumberWithLocale( value );
	}

	@SuppressLint( "NewApi" )
	@Override
	public AccessibilityNodeProvider getAccessibilityNodeProvider() {
		if ( !this.mHasSelectorWheel ) {
			return super.getAccessibilityNodeProvider();
		}
		if ( this.mAccessibilityNodeProvider == null ) {
			this.mAccessibilityNodeProvider =
					new SupportAccessibilityNodeProvider();
		}
		return this.mAccessibilityNodeProvider.mProvider;
	}

	@Override
	protected float getBottomFadingEdgeStrength() {
		return NumberPicker.TOP_AND_BOTTOM_FADING_EDGE_STRENGTH;
	}

	/**
	 * Gets the values to be displayed instead of string values.
	 * 
	 * @return The displayed values.
	 */
	public String[] getDisplayedValues() {
		return this.mDisplayedValues;
	}

	/**
	 * Returns the max value of the picker.
	 * 
	 * @return The max value.
	 */
	public int getMaxValue() {
		return this.mMaxValue;
	}

	/**
	 * Returns the min value of the picker.
	 * 
	 * @return The min value
	 */
	public int getMinValue() {
		return this.mMinValue;
	}

	/**
	 * @return The selected index given its displayed <code>value</code>.
	 */
	private int getSelectedPos( String value ) {
		int bestMatchPosition = this.mMinValue;

		if ( this.mDisplayedValues == null ) {
			try {
				bestMatchPosition = Integer.parseInt( value );
			} catch ( final NumberFormatException numberFormatException ) {
				// Ignore as if it's not a number we don't care
			}
		} else {
			value = value.toLowerCase();

			for ( int i = 0; i < this.mDisplayedValues.length; ++i ) {
				// Don't force the user to type in jan when ja will do
				final String currentDisplayedValue =
						this.mDisplayedValues[ i ].toLowerCase();

				if ( value.equals( currentDisplayedValue ) ) {
					bestMatchPosition = this.mMinValue + i;

					break;
				} else {
					if ( ( bestMatchPosition != this.mMinValue )
							&& currentDisplayedValue.startsWith( value ) ) {
						bestMatchPosition = this.mMinValue + i;
					}
				}
			}

			if ( bestMatchPosition == this.mMinValue ) {
				/*
				 * The user might have typed in a number into the month field
				 * i.e. 10 instead of OCT so support that too.
				 */
				try {
					bestMatchPosition = Integer.parseInt( value );
				} catch ( final NumberFormatException numberFormatException ) {
					// Ignore as if it's not a number we don't care
				}
			}
		}

		return bestMatchPosition;
	}

	@Override
	public int getSolidColor() {
		return this.mSolidColor;
	}

	private SupportAccessibilityNodeProvider getSupportAccessibilityNodeProvider() {
		return new SupportAccessibilityNodeProvider();
	}

	@Override
	protected float getTopFadingEdgeStrength() {
		return NumberPicker.TOP_AND_BOTTOM_FADING_EDGE_STRENGTH;
	}

	/**
	 * Returns the value of the picker.
	 * 
	 * @return The value.
	 */
	public int getValue() {
		return this.mValue;
	}

	/**
	 * @return The wrapped index <code>selectorIndex</code> value.
	 */
	private int getWrappedSelectorIndex( final int selectorIndex ) {
		if ( selectorIndex > this.mMaxValue ) {
			return ( this.mMinValue + ( ( selectorIndex - this.mMaxValue ) % ( this.mMaxValue - this.mMinValue ) ) ) - 1;
		} else if ( selectorIndex < this.mMinValue ) {
			return ( this.mMaxValue - ( ( this.mMinValue - selectorIndex ) % ( this.mMaxValue - this.mMinValue ) ) ) + 1;
		}
		return selectorIndex;
	}

	/**
	 * Gets whether the selector wheel wraps when reaching the min/max value.
	 * 
	 * @return True if the selector wheel wraps.
	 * 
	 * @see #getMinValue()
	 * @see #getMaxValue()
	 */
	public boolean getWrapSelectorWheel() {
		return this.mWrapSelectorWheel;
	}

	/**
	 * Hides the soft input if it is active for the input text.
	 */
	private void hideSoftInput() {
		final InputMethodManager inputMethodManager =
				(InputMethodManager) this.getContext().getSystemService(
						Context.INPUT_METHOD_SERVICE );
		if ( ( inputMethodManager != null )
				&& inputMethodManager.isActive( this.mInputText ) ) {
			inputMethodManager.hideSoftInputFromWindow( this.getWindowToken(),
					0 );
			if ( this.mHasSelectorWheel ) {
				this.mInputText.setVisibility( View.INVISIBLE );
			}
		}
	}

	/**
	 * Increments the <code>selectorIndices</code> whose string representations
	 * will be displayed in the selector.
	 */
	private void incrementSelectorIndices( final int[] selectorIndices ) {
		for ( int i = 0; i < ( selectorIndices.length - 1 ); i++ ) {
			selectorIndices[ i ] = selectorIndices[ i + 1 ];
		}
		int nextScrollSelectorIndex =
				selectorIndices[ selectorIndices.length - 2 ] + 1;
		if ( this.mWrapSelectorWheel
				&& ( nextScrollSelectorIndex > this.mMaxValue ) ) {
			nextScrollSelectorIndex = this.mMinValue;
		}
		selectorIndices[ selectorIndices.length - 1 ] = nextScrollSelectorIndex;
		this.ensureCachedScrollSelectorValue( nextScrollSelectorIndex );
	}

	private void initializeFadingEdges() {
		this.setVerticalFadingEdgeEnabled( true );
		this.setFadingEdgeLength( ( this.getBottom() - this.getTop() - this.mTextSize ) / 2 );
	}

	private void initializeSelectorWheel() {
		this.initializeSelectorWheelIndices();
		final int[] selectorIndices = this.mSelectorIndices;
		final int totalTextHeight = selectorIndices.length * this.mTextSize;
		final float totalTextGapHeight =
				( this.getBottom() - this.getTop() ) - totalTextHeight;
		final float textGapCount = selectorIndices.length;
		this.mSelectorTextGapHeight =
				(int) ( ( totalTextGapHeight / textGapCount ) + 0.5f );
		this.mSelectorElementHeight =
				this.mTextSize + this.mSelectorTextGapHeight;
		// Ensure that the middle item is positioned the same as the text in
		// mInputText
		final int editTextTextPosition =
				this.mInputText.getBaseline() + this.mInputText.getTop();
		this.mInitialScrollOffset =
				editTextTextPosition
						- ( this.mSelectorElementHeight * NumberPicker.SELECTOR_MIDDLE_ITEM_INDEX );
		this.mCurrentScrollOffset = this.mInitialScrollOffset;
		this.updateInputTextView();
	}

	/**
	 * Resets the selector indices and clear the cached string representation of
	 * these indices.
	 */
	private void initializeSelectorWheelIndices() {
		this.mSelectorIndexToStringCache.clear();
		final int[] selectorIndices = this.mSelectorIndices;
		final int current = this.getValue();
		for ( int i = 0; i < this.mSelectorIndices.length; i++ ) {
			int selectorIndex =
					current + ( i - NumberPicker.SELECTOR_MIDDLE_ITEM_INDEX );
			if ( this.mWrapSelectorWheel ) {
				selectorIndex = this.getWrappedSelectorIndex( selectorIndex );
			}
			selectorIndices[ i ] = selectorIndex;
			this.ensureCachedScrollSelectorValue( selectorIndices[ i ] );
		}
	}

	/**
	 * Makes a measure spec that tries greedily to use the max value.
	 * 
	 * @param measureSpec
	 *            The measure spec.
	 * @param maxSize
	 *            The max value for the size.
	 * @return A measure spec greedily imposing the max size.
	 */
	private int makeMeasureSpec( final int measureSpec, final int maxSize ) {
		if ( maxSize == NumberPicker.SIZE_UNSPECIFIED ) {
			return measureSpec;
		}
		final int size = MeasureSpec.getSize( measureSpec );
		final int mode = MeasureSpec.getMode( measureSpec );
		switch ( mode ) {
		case MeasureSpec.EXACTLY:
			return measureSpec;
		case MeasureSpec.AT_MOST:
			return MeasureSpec.makeMeasureSpec( Math.min( size, maxSize ),
					MeasureSpec.EXACTLY );
		case MeasureSpec.UNSPECIFIED:
			return MeasureSpec.makeMeasureSpec( maxSize, MeasureSpec.EXACTLY );
		default:
			throw new IllegalArgumentException( "Unknown measure mode: " + mode );
		}
	}

	/**
	 * Move to the final position of a scroller. Ensures to force finish the
	 * scroller and if it is not at its final position a scroll of the selector
	 * wheel is performed to fast forward to the final position.
	 * 
	 * @param scroller
	 *            The scroller to whose final position to get.
	 * @return True of the a move was performed, i.e. the scroller was not in
	 *         final position.
	 */
	private boolean moveToFinalScrollerPosition( final Scroller scroller ) {
		scroller.forceFinished( true );
		int amountToScroll = scroller.getFinalY() - scroller.getCurrY();
		final int futureScrollOffset =
				( this.mCurrentScrollOffset + amountToScroll )
						% this.mSelectorElementHeight;
		int overshootAdjustment =
				this.mInitialScrollOffset - futureScrollOffset;
		if ( overshootAdjustment != 0 ) {
			if ( Math.abs( overshootAdjustment ) > ( this.mSelectorElementHeight / 2 ) ) {
				if ( overshootAdjustment > 0 ) {
					overshootAdjustment -= this.mSelectorElementHeight;
				} else {
					overshootAdjustment += this.mSelectorElementHeight;
				}
			}
			amountToScroll += overshootAdjustment;
			this.scrollBy( 0, amountToScroll );
			return true;
		}
		return false;
	}

	/**
	 * Notifies the listener, if registered, of a change of the value of this
	 * NumberPicker.
	 */
	private void notifyChange( final int previous, final int current ) {
		if ( this.mOnValueChangeListener != null ) {
			this.mOnValueChangeListener.onValueChange( this, previous,
					this.mValue );
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		this.removeAllCallbacks();
	}

	@Override
	protected void onDraw( final Canvas canvas ) {
		if ( !this.mHasSelectorWheel ) {
			super.onDraw( canvas );
			return;
		}
		final float x = ( this.getRight() - this.getLeft() ) / 2;
		float y = this.mCurrentScrollOffset;

		// draw the virtual buttons pressed state if needed
		if ( ( this.mVirtualButtonPressedDrawable != null )
				&& ( this.mScrollState == OnScrollListener.SCROLL_STATE_IDLE ) ) {
			if ( this.mDecrementVirtualButtonPressed ) {
				// mVirtualButtonPressedDrawable.setState(PRESSED_STATE_SET);
				this.mVirtualButtonPressedDrawable.setState( View.PRESSED_ENABLED_STATE_SET );
				this.mVirtualButtonPressedDrawable.setBounds( 0, 0,
						this.getRight(), this.mTopSelectionDividerTop );
				this.mVirtualButtonPressedDrawable.draw( canvas );
			}
			if ( this.mIncrementVirtualButtonPressed ) {
				// mVirtualButtonPressedDrawable.setState(PRESSED_STATE_SET);
				this.mVirtualButtonPressedDrawable.setState( View.PRESSED_ENABLED_STATE_SET );
				this.mVirtualButtonPressedDrawable.setBounds( 0,
						this.mBottomSelectionDividerBottom, this.getRight(),
						this.getBottom() );
				this.mVirtualButtonPressedDrawable.draw( canvas );
			}
		}

		// draw the selector wheel
		final int[] selectorIndices = this.mSelectorIndices;
		for ( int i = 0; i < selectorIndices.length; i++ ) {
			final int selectorIndex = selectorIndices[ i ];
			final String scrollSelectorValue =
					this.mSelectorIndexToStringCache.get( selectorIndex );
			// Do not draw the middle item if input is visible since the input
			// is shown only if the wheel is static and it covers the middle
			// item. Otherwise, if the user starts editing the text via the
			// IME he may see a dimmed version of the old value intermixed
			// with the new one.
			if ( ( i != NumberPicker.SELECTOR_MIDDLE_ITEM_INDEX )
					|| ( this.mInputText.getVisibility() != View.VISIBLE ) ) {
				canvas.drawText( scrollSelectorValue, x, y,
						this.mSelectorWheelPaint );
			}
			y += this.mSelectorElementHeight;
		}

		// draw the selection dividers
		if ( this.mSelectionDivider != null ) {
			// draw the top divider
			final int topOfTopDivider = this.mTopSelectionDividerTop;
			final int bottomOfTopDivider =
					topOfTopDivider + this.mSelectionDividerHeight;
			this.mSelectionDivider.setBounds( 0, topOfTopDivider,
					this.getRight(), bottomOfTopDivider );
			this.mSelectionDivider.draw( canvas );

			// draw the bottom divider
			final int bottomOfBottomDivider =
					this.mBottomSelectionDividerBottom;
			final int topOfBottomDivider =
					bottomOfBottomDivider - this.mSelectionDividerHeight;
			this.mSelectionDivider.setBounds( 0, topOfBottomDivider,
					this.getRight(), bottomOfBottomDivider );
			this.mSelectionDivider.draw( canvas );
		}
	}

	@SuppressLint( "NewApi" )
	@Override
	public void onInitializeAccessibilityEvent( final AccessibilityEvent event ) {
		super.onInitializeAccessibilityEvent( event );
		event.setClassName( NumberPicker.class.getName() );
		event.setScrollable( true );
		event.setScrollY( ( this.mMinValue + this.mValue )
				* this.mSelectorElementHeight );
		event.setMaxScrollY( ( this.mMaxValue - this.mMinValue )
				* this.mSelectorElementHeight );
	}

	@Override
	public boolean onInterceptTouchEvent( final MotionEvent event ) {
		if ( !this.mHasSelectorWheel || !this.isEnabled() ) {
			return false;
		}
		final int action = event.getAction() & MotionEvent.ACTION_MASK;
		switch ( action ) {
		case MotionEvent.ACTION_DOWN: {
			this.removeAllCallbacks();
			this.mInputText.setVisibility( View.INVISIBLE );
			this.mLastDownOrMoveEventY = this.mLastDownEventY = event.getY();
			this.mLastDownEventTime = event.getEventTime();
			this.mIngonreMoveEvents = false;
			this.mShowSoftInputOnTap = false;
			// Handle pressed state before any state change.
			if ( this.mLastDownEventY < this.mTopSelectionDividerTop ) {
				if ( this.mScrollState == OnScrollListener.SCROLL_STATE_IDLE ) {
					this.mPressedStateHelper.buttonPressDelayed( PressedStateHelper.BUTTON_DECREMENT );
				}
			} else if ( this.mLastDownEventY > this.mBottomSelectionDividerBottom ) {
				if ( this.mScrollState == OnScrollListener.SCROLL_STATE_IDLE ) {
					this.mPressedStateHelper.buttonPressDelayed( PressedStateHelper.BUTTON_INCREMENT );
				}
			}
			// Make sure we support flinging inside scrollables.
			this.getParent().requestDisallowInterceptTouchEvent( true );
			if ( !this.mFlingScroller.isFinished() ) {
				this.mFlingScroller.forceFinished( true );
				this.mAdjustScroller.forceFinished( true );
				this.onScrollStateChange( OnScrollListener.SCROLL_STATE_IDLE );
			} else if ( !this.mAdjustScroller.isFinished() ) {
				this.mFlingScroller.forceFinished( true );
				this.mAdjustScroller.forceFinished( true );
			} else if ( this.mLastDownEventY < this.mTopSelectionDividerTop ) {
				this.hideSoftInput();
				this.postChangeCurrentByOneFromLongPress( false,
						ViewConfiguration.getLongPressTimeout() );
			} else if ( this.mLastDownEventY > this.mBottomSelectionDividerBottom ) {
				this.hideSoftInput();
				this.postChangeCurrentByOneFromLongPress( true,
						ViewConfiguration.getLongPressTimeout() );
			} else {
				this.mShowSoftInputOnTap = true;
				this.postBeginSoftInputOnLongPressCommand();
			}
			return true;
		}
		}
		return false;
	}

	@Override
	protected void onLayout( final boolean changed, final int left,
			final int top, final int right, final int bottom ) {
		if ( !this.mHasSelectorWheel ) {
			super.onLayout( changed, left, top, right, bottom );
			return;
		}
		final int msrdWdth = this.getMeasuredWidth();
		final int msrdHght = this.getMeasuredHeight();

		// Input text centered horizontally.
		final int inptTxtMsrdWdth = this.mInputText.getMeasuredWidth();
		final int inptTxtMsrdHght = this.mInputText.getMeasuredHeight();
		final int inptTxtLeft = ( msrdWdth - inptTxtMsrdWdth ) / 2;
		final int inptTxtTop = ( msrdHght - inptTxtMsrdHght ) / 2;
		final int inptTxtRight = inptTxtLeft + inptTxtMsrdWdth;
		final int inptTxtBottom = inptTxtTop + inptTxtMsrdHght;
		this.mInputText.layout( inptTxtLeft, inptTxtTop, inptTxtRight,
				inptTxtBottom );

		if ( changed ) {
			// need to do all this when we know our size
			this.initializeSelectorWheel();
			this.initializeFadingEdges();
			this.mTopSelectionDividerTop =
					( ( this.getHeight() - this.mSelectionDividersDistance ) / 2 )
							- this.mSelectionDividerHeight;
			this.mBottomSelectionDividerBottom =
					this.mTopSelectionDividerTop
							+ ( 2 * this.mSelectionDividerHeight )
							+ this.mSelectionDividersDistance;
		}
	}

	@Override
	protected void onMeasure( final int widthMeasureSpec,
			final int heightMeasureSpec ) {
		if ( !this.mHasSelectorWheel ) {
			super.onMeasure( widthMeasureSpec, heightMeasureSpec );
			return;
		}
		// Try greedily to fit the max width and height.
		final int newWidthMeasureSpec =
				this.makeMeasureSpec( widthMeasureSpec, this.mMaxWidth );
		final int newHeightMeasureSpec =
				this.makeMeasureSpec( heightMeasureSpec, this.mMaxHeight );
		super.onMeasure( newWidthMeasureSpec, newHeightMeasureSpec );
		// Flag if we are measured with width or height less than the respective
		// min.
		final int widthSize =
				this.resolveSizeAndStateRespectingMinSize( this.mMinWidth,
						this.getMeasuredWidth(), widthMeasureSpec );
		final int heightSize =
				this.resolveSizeAndStateRespectingMinSize( this.mMinHeight,
						this.getMeasuredHeight(), heightMeasureSpec );
		this.setMeasuredDimension( widthSize, heightSize );
	}

	/**
	 * Callback invoked upon completion of a given <code>scroller</code>.
	 */
	private void onScrollerFinished( final Scroller scroller ) {
		if ( scroller == this.mFlingScroller ) {
			if ( !this.ensureScrollWheelAdjusted() ) {
				this.updateInputTextView();
			}
			this.onScrollStateChange( OnScrollListener.SCROLL_STATE_IDLE );
		} else {
			if ( this.mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL ) {
				this.updateInputTextView();
			}
		}
	}

	/**
	 * Handles transition to a given <code>scrollState</code>
	 */
	private void onScrollStateChange( final int scrollState ) {
		if ( this.mScrollState == scrollState ) {
			return;
		}
		this.mScrollState = scrollState;
		if ( this.mOnScrollListener != null ) {
			this.mOnScrollListener.onScrollStateChange( this, scrollState );
		}
	}

	@Override
	public boolean onTouchEvent( final MotionEvent event ) {
		if ( !this.isEnabled() || !this.mHasSelectorWheel ) {
			return false;
		}
		if ( this.mVelocityTracker == null ) {
			this.mVelocityTracker = VelocityTracker.obtain();
		}
		this.mVelocityTracker.addMovement( event );
		final int action = event.getAction() & MotionEvent.ACTION_MASK;
		switch ( action ) {
		case MotionEvent.ACTION_MOVE: {
			if ( this.mIngonreMoveEvents ) {
				break;
			}
			final float currentMoveY = event.getY();
			if ( this.mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL ) {
				final int deltaDownY =
						(int) Math.abs( currentMoveY - this.mLastDownEventY );
				if ( deltaDownY > this.mTouchSlop ) {
					this.removeAllCallbacks();
					this.onScrollStateChange( OnScrollListener.SCROLL_STATE_TOUCH_SCROLL );
				}
			} else {
				final int deltaMoveY =
						(int) ( ( currentMoveY - this.mLastDownOrMoveEventY ) );
				this.scrollBy( 0, deltaMoveY );
				this.invalidate();
			}
			this.mLastDownOrMoveEventY = currentMoveY;
		}
			break;
		case MotionEvent.ACTION_UP: {
			this.removeBeginSoftInputCommand();
			this.removeChangeCurrentByOneFromLongPress();
			this.mPressedStateHelper.cancel();
			final VelocityTracker velocityTracker = this.mVelocityTracker;
			velocityTracker.computeCurrentVelocity( 1000,
					this.mMaximumFlingVelocity );
			final int initialVelocity = (int) velocityTracker.getYVelocity();
			if ( Math.abs( initialVelocity ) > this.mMinimumFlingVelocity ) {
				this.fling( initialVelocity );
				this.onScrollStateChange( OnScrollListener.SCROLL_STATE_FLING );
			} else {
				final int eventY = (int) event.getY();
				final int deltaMoveY =
						(int) Math.abs( eventY - this.mLastDownEventY );
				final long deltaTime =
						event.getEventTime() - this.mLastDownEventTime;
				final long tapTimeout = ViewConfiguration.getTapTimeout();
				if ( deltaMoveY <= this.mTouchSlop ) { // && deltaTime <
														// ViewConfiguration.getTapTimeout())
														// {
					if ( this.mShowSoftInputOnTap ) {
						this.mShowSoftInputOnTap = false;
						this.showSoftInput();
					} else {
						final int selectorIndexOffset =
								( eventY / this.mSelectorElementHeight )
										- NumberPicker.SELECTOR_MIDDLE_ITEM_INDEX;
						if ( selectorIndexOffset > 0 ) {
							this.changeValueByOne( true );
							this.mPressedStateHelper.buttonTapped( PressedStateHelper.BUTTON_INCREMENT );
						} else if ( selectorIndexOffset < 0 ) {
							this.changeValueByOne( false );
							this.mPressedStateHelper.buttonTapped( PressedStateHelper.BUTTON_DECREMENT );
						}
					}
				} else {
					this.ensureScrollWheelAdjusted();
				}
				this.onScrollStateChange( OnScrollListener.SCROLL_STATE_IDLE );
			}
			this.mVelocityTracker.recycle();
			this.mVelocityTracker = null;
		}
			break;
		}
		return true;
	}

	/**
	 * Posts a command for beginning an edit of the current value via IME on
	 * long press.
	 */
	private void postBeginSoftInputOnLongPressCommand() {
		if ( this.mBeginSoftInputOnLongPressCommand == null ) {
			this.mBeginSoftInputOnLongPressCommand =
					new BeginSoftInputOnLongPressCommand();
		} else {
			this.removeCallbacks( this.mBeginSoftInputOnLongPressCommand );
		}
		this.postDelayed( this.mBeginSoftInputOnLongPressCommand,
				ViewConfiguration.getLongPressTimeout() );
	}

	/**
	 * Posts a command for changing the current value by one.
	 * 
	 * @param increment
	 *            Whether to increment or decrement the value.
	 */
	private void postChangeCurrentByOneFromLongPress( final boolean increment,
			final long delayMillis ) {
		if ( this.mChangeCurrentByOneFromLongPressCommand == null ) {
			this.mChangeCurrentByOneFromLongPressCommand =
					new ChangeCurrentByOneFromLongPressCommand();
		} else {
			this.removeCallbacks( this.mChangeCurrentByOneFromLongPressCommand );
		}
		this.mChangeCurrentByOneFromLongPressCommand.setStep( increment );
		this.postDelayed( this.mChangeCurrentByOneFromLongPressCommand,
				delayMillis );
	}

	/**
	 * Posts an {@link SetSelectionCommand} from the given <code>selectionStart
	 * </code> to <code>selectionEnd</code>.
	 */
	private void postSetSelectionCommand( final int selectionStart,
			final int selectionEnd ) {
		if ( this.mSetSelectionCommand == null ) {
			this.mSetSelectionCommand = new SetSelectionCommand();
		} else {
			this.removeCallbacks( this.mSetSelectionCommand );
		}
		this.mSetSelectionCommand.mSelectionStart = selectionStart;
		this.mSetSelectionCommand.mSelectionEnd = selectionEnd;
		this.post( this.mSetSelectionCommand );
	}

	/**
	 * Removes all pending callback from the message queue.
	 */
	private void removeAllCallbacks() {
		if ( this.mChangeCurrentByOneFromLongPressCommand != null ) {
			this.removeCallbacks( this.mChangeCurrentByOneFromLongPressCommand );
		}
		if ( this.mSetSelectionCommand != null ) {
			this.removeCallbacks( this.mSetSelectionCommand );
		}
		if ( this.mBeginSoftInputOnLongPressCommand != null ) {
			this.removeCallbacks( this.mBeginSoftInputOnLongPressCommand );
		}
		this.mPressedStateHelper.cancel();
	}

	/**
	 * Removes the command for beginning an edit of the current value via IME.
	 */
	private void removeBeginSoftInputCommand() {
		if ( this.mBeginSoftInputOnLongPressCommand != null ) {
			this.removeCallbacks( this.mBeginSoftInputOnLongPressCommand );
		}
	}

	/**
	 * Removes the command for changing the current value by one.
	 */
	private void removeChangeCurrentByOneFromLongPress() {
		if ( this.mChangeCurrentByOneFromLongPressCommand != null ) {
			this.removeCallbacks( this.mChangeCurrentByOneFromLongPressCommand );
		}
	}

	/**
	 * Utility to reconcile a desired size and state, with constraints imposed
	 * by a MeasureSpec. Tries to respect the min size, unless a different size
	 * is imposed by the constraints.
	 * 
	 * @param minSize
	 *            The minimal desired size.
	 * @param measuredSize
	 *            The currently measured size.
	 * @param measureSpec
	 *            The current measure spec.
	 * @return The resolved size and state.
	 */
	@SuppressLint( "NewApi" )
	private int resolveSizeAndStateRespectingMinSize( final int minSize,
			final int measuredSize, final int measureSpec ) {
		if ( minSize != NumberPicker.SIZE_UNSPECIFIED ) {
			final int desiredWidth = Math.max( minSize, measuredSize );
			return NumberPicker.resolveSizeAndState( desiredWidth, measureSpec,
					0 );
		} else {
			return measuredSize;
		}
	}

	@Override
	public void scrollBy( final int x, final int y ) {
		final int[] selectorIndices = this.mSelectorIndices;
		if ( !this.mWrapSelectorWheel
				&& ( y > 0 )
				&& ( selectorIndices[ NumberPicker.SELECTOR_MIDDLE_ITEM_INDEX ] <= this.mMinValue ) ) {
			this.mCurrentScrollOffset = this.mInitialScrollOffset;
			return;
		}
		if ( !this.mWrapSelectorWheel
				&& ( y < 0 )
				&& ( selectorIndices[ NumberPicker.SELECTOR_MIDDLE_ITEM_INDEX ] >= this.mMaxValue ) ) {
			this.mCurrentScrollOffset = this.mInitialScrollOffset;
			return;
		}
		this.mCurrentScrollOffset += y;
		while ( ( this.mCurrentScrollOffset - this.mInitialScrollOffset ) > this.mSelectorTextGapHeight ) {
			this.mCurrentScrollOffset -= this.mSelectorElementHeight;
			this.decrementSelectorIndices( selectorIndices );
			this.setValueInternal(
					selectorIndices[ NumberPicker.SELECTOR_MIDDLE_ITEM_INDEX ],
					true );
			if ( !this.mWrapSelectorWheel
					&& ( selectorIndices[ NumberPicker.SELECTOR_MIDDLE_ITEM_INDEX ] <= this.mMinValue ) ) {
				this.mCurrentScrollOffset = this.mInitialScrollOffset;
			}
		}
		while ( ( this.mCurrentScrollOffset - this.mInitialScrollOffset ) < -this.mSelectorTextGapHeight ) {
			this.mCurrentScrollOffset += this.mSelectorElementHeight;
			this.incrementSelectorIndices( selectorIndices );
			this.setValueInternal(
					selectorIndices[ NumberPicker.SELECTOR_MIDDLE_ITEM_INDEX ],
					true );
			if ( !this.mWrapSelectorWheel
					&& ( selectorIndices[ NumberPicker.SELECTOR_MIDDLE_ITEM_INDEX ] >= this.mMaxValue ) ) {
				this.mCurrentScrollOffset = this.mInitialScrollOffset;
			}
		}
	}

	/**
	 * Sets the values to be displayed.
	 * 
	 * @param displayedValues
	 *            The displayed values.
	 * 
	 *            <strong>Note:</strong> The length of the displayed values
	 *            array must be equal to the range of selectable numbers which
	 *            is equal to {@link #getMaxValue()} - {@link #getMinValue()} +
	 *            1.
	 */
	public void setDisplayedValues( final String[] displayedValues ) {
		if ( this.mDisplayedValues != displayedValues ) {
			this.mDisplayedValues = displayedValues;
			this.updateInputTextView();
			this.initializeSelectorWheelIndices();
			this.tryComputeMaxWidth();
		}
	}

	@Override
	public void setEnabled( final boolean enabled ) {
		super.setEnabled( enabled );
		if ( !this.mHasSelectorWheel ) {
			this.mIncrementButton.setEnabled( enabled );
		}
		if ( !this.mHasSelectorWheel ) {
			this.mDecrementButton.setEnabled( enabled );
		}
		this.mInputText.setEnabled( enabled );
	}

	/**
	 * Set the formatter to be used for formatting the current value.
	 * <p>
	 * Note: If you have provided alternative values for the values this
	 * formatter is never invoked.
	 * </p>
	 * 
	 * @param formatter
	 *            The formatter object. If formatter is <code>null</code>,
	 *            {@link String#valueOf(int)} will be used.
	 * @see #setDisplayedValues(String[])
	 */
	public void setFormatter( final Formatter formatter ) {
		if ( formatter == this.mFormatter ) {
			return;
		}
		this.mFormatter = formatter;
		this.initializeSelectorWheelIndices();
		this.updateInputTextView();
	}

	/**
	 * Sets the max value of the picker.
	 * 
	 * @param maxValue
	 *            The max value inclusive.
	 * 
	 *            <strong>Note:</strong> The length of the displayed values
	 *            array set via {@link #setDisplayedValues(String[])} must be
	 *            equal to the range of selectable numbers which is equal to
	 *            {@link #getMaxValue()} - {@link #getMinValue()} + 1.
	 */
	public void setMaxValue( final int maxValue ) {
		if ( this.mMaxValue == maxValue ) {
			return;
		}
		if ( maxValue < 0 ) {
			throw new IllegalArgumentException( "maxValue must be >= 0" );
		}
		this.mMaxValue = maxValue;
		if ( this.mMaxValue < this.mValue ) {
			this.mValue = this.mMaxValue;
		}
		final boolean wrapSelectorWheel =
				( this.mMaxValue - this.mMinValue ) > this.mSelectorIndices.length;
		this.setWrapSelectorWheel( wrapSelectorWheel );
		this.initializeSelectorWheelIndices();
		this.updateInputTextView();
		this.tryComputeMaxWidth();
		this.invalidate();
	}

	/**
	 * Sets the min value of the picker.
	 * 
	 * @param minValue
	 *            The min value inclusive.
	 * 
	 *            <strong>Note:</strong> The length of the displayed values
	 *            array set via {@link #setDisplayedValues(String[])} must be
	 *            equal to the range of selectable numbers which is equal to
	 *            {@link #getMaxValue()} - {@link #getMinValue()} + 1.
	 */
	public void setMinValue( final int minValue ) {
		if ( this.mMinValue == minValue ) {
			return;
		}
		if ( minValue < 0 ) {
			throw new IllegalArgumentException( "minValue must be >= 0" );
		}
		this.mMinValue = minValue;
		if ( this.mMinValue > this.mValue ) {
			this.mValue = this.mMinValue;
		}
		final boolean wrapSelectorWheel =
				( this.mMaxValue - this.mMinValue ) > this.mSelectorIndices.length;
		this.setWrapSelectorWheel( wrapSelectorWheel );
		this.initializeSelectorWheelIndices();
		this.updateInputTextView();
		this.tryComputeMaxWidth();
		this.invalidate();
	}

	/**
	 * Sets the speed at which the numbers be incremented and decremented when
	 * the up and down buttons are long pressed respectively.
	 * <p>
	 * The default value is 300 ms.
	 * </p>
	 * 
	 * @param intervalMillis
	 *            The speed (in milliseconds) at which the numbers will be
	 *            incremented and decremented.
	 */
	public void setOnLongPressUpdateInterval( final long intervalMillis ) {
		this.mLongPressUpdateInterval = intervalMillis;
	}

	/**
	 * Set listener to be notified for scroll state changes.
	 * 
	 * @param onScrollListener
	 *            The listener.
	 */
	public void setOnScrollListener( final OnScrollListener onScrollListener ) {
		this.mOnScrollListener = onScrollListener;
	}

	/**
	 * Sets the listener to be notified on change of the current value.
	 * 
	 * @param onValueChangedListener
	 *            The listener.
	 */
	public void setOnValueChangedListener(
			final OnValueChangeListener onValueChangedListener ) {
		this.mOnValueChangeListener = onValueChangedListener;
	}

	/**
	 * Set the current value for the number picker.
	 * <p>
	 * If the argument is less than the {@link NumberPicker#getMinValue()} and
	 * {@link NumberPicker#getWrapSelectorWheel()} is <code>false</code> the
	 * current value is set to the {@link NumberPicker#getMinValue()} value.
	 * </p>
	 * <p>
	 * If the argument is less than the {@link NumberPicker#getMinValue()} and
	 * {@link NumberPicker#getWrapSelectorWheel()} is <code>true</code> the
	 * current value is set to the {@link NumberPicker#getMaxValue()} value.
	 * </p>
	 * <p>
	 * If the argument is less than the {@link NumberPicker#getMaxValue()} and
	 * {@link NumberPicker#getWrapSelectorWheel()} is <code>false</code> the
	 * current value is set to the {@link NumberPicker#getMaxValue()} value.
	 * </p>
	 * <p>
	 * If the argument is less than the {@link NumberPicker#getMaxValue()} and
	 * {@link NumberPicker#getWrapSelectorWheel()} is <code>true</code> the
	 * current value is set to the {@link NumberPicker#getMinValue()} value.
	 * </p>
	 * 
	 * @param value
	 *            The current value.
	 * @see #setWrapSelectorWheel(boolean)
	 * @see #setMinValue(int)
	 * @see #setMaxValue(int)
	 */
	public void setValue( final int value ) {
		this.setValueInternal( value, false );
	}

	/**
	 * Sets the current value of this NumberPicker.
	 * 
	 * @param current
	 *            The new value of the NumberPicker.
	 * @param notifyChange
	 *            Whether to notify if the current value changed.
	 */
	private void setValueInternal( int current, final boolean notifyChange ) {
		if ( this.mValue == current ) {
			return;
		}
		// Wrap around the values if we go past the start or end
		if ( this.mWrapSelectorWheel ) {
			current = this.getWrappedSelectorIndex( current );
		} else {
			current = Math.max( current, this.mMinValue );
			current = Math.min( current, this.mMaxValue );
		}
		final int previous = this.mValue;
		this.mValue = current;
		this.updateInputTextView();
		if ( notifyChange ) {
			this.notifyChange( previous, current );
		}
		this.initializeSelectorWheelIndices();
		this.invalidate();
	}

	/**
	 * Sets whether the selector wheel shown during flinging/scrolling should
	 * wrap around the {@link NumberPicker#getMinValue()} and
	 * {@link NumberPicker#getMaxValue()} values.
	 * <p>
	 * By default if the range (max - min) is more than the number of items
	 * shown on the selector wheel the selector wheel wrapping is enabled.
	 * </p>
	 * <p>
	 * <strong>Note:</strong> If the number of items, i.e. the range (
	 * {@link #getMaxValue()} - {@link #getMinValue()}) is less than the number
	 * of items shown on the selector wheel, the selector wheel will not wrap.
	 * Hence, in such a case calling this method is a NOP.
	 * </p>
	 * 
	 * @param wrapSelectorWheel
	 *            Whether to wrap.
	 */
	public void setWrapSelectorWheel( final boolean wrapSelectorWheel ) {
		final boolean wrappingAllowed =
				( this.mMaxValue - this.mMinValue ) >= this.mSelectorIndices.length;
		if ( ( !wrapSelectorWheel || wrappingAllowed )
				&& ( wrapSelectorWheel != this.mWrapSelectorWheel ) ) {
			this.mWrapSelectorWheel = wrapSelectorWheel;
		}
	}

	/**
	 * Shows the soft input for its input text.
	 */
	private void showSoftInput() {
		final InputMethodManager inputMethodManager =
				(InputMethodManager) this.getContext().getSystemService(
						Context.INPUT_METHOD_SERVICE );
		if ( inputMethodManager != null ) {
			if ( this.mHasSelectorWheel ) {
				this.mInputText.setVisibility( View.VISIBLE );
			}
			this.mInputText.requestFocus();
			inputMethodManager.showSoftInput( this.mInputText, 0 );
		}
	}

	/**
	 * Computes the max width if no such specified as an attribute.
	 */
	private void tryComputeMaxWidth() {
		if ( !this.mComputeMaxWidth ) {
			return;
		}
		int maxTextWidth = 0;
		if ( this.mDisplayedValues == null ) {
			float maxDigitWidth = 0;
			for ( int i = 0; i <= 9; i++ ) {
				final float digitWidth =
						this.mSelectorWheelPaint.measureText( NumberPicker.formatNumberWithLocale( i ) );
				if ( digitWidth > maxDigitWidth ) {
					maxDigitWidth = digitWidth;
				}
			}
			int numberOfDigits = 0;
			int current = this.mMaxValue;
			while ( current > 0 ) {
				numberOfDigits++;
				current = current / 10;
			}
			maxTextWidth = (int) ( numberOfDigits * maxDigitWidth );
		} else {
			final int valueCount = this.mDisplayedValues.length;
			for ( int i = 0; i < valueCount; i++ ) {
				final float textWidth =
						this.mSelectorWheelPaint.measureText( this.mDisplayedValues[ i ] );
				if ( textWidth > maxTextWidth ) {
					maxTextWidth = (int) textWidth;
				}
			}
		}
		maxTextWidth +=
				this.mInputText.getPaddingLeft()
						+ this.mInputText.getPaddingRight();
		if ( this.mMaxWidth != maxTextWidth ) {
			if ( maxTextWidth > this.mMinWidth ) {
				this.mMaxWidth = maxTextWidth;
			} else {
				this.mMaxWidth = this.mMinWidth;
			}
			this.invalidate();
		}
	}

	/**
	 * Updates the view of this NumberPicker. If displayValues were specified in
	 * the string corresponding to the index specified by the current value will
	 * be returned. Otherwise, the formatter specified in {@link #setFormatter}
	 * will be used to format the number.
	 * 
	 * @return Whether the text was updated.
	 */
	private boolean updateInputTextView() {
		/*
		 * If we don't have displayed values then use the current number else
		 * find the correct value in the displayed values for the current
		 * number.
		 */
		final String text =
				( this.mDisplayedValues == null ) ? this.formatNumber( this.mValue )
						: this.mDisplayedValues[ this.mValue - this.mMinValue ];
		if ( !TextUtils.isEmpty( text )
				&& !text.equals( this.mInputText.getText().toString() ) ) {
			this.mInputText.setText( text );
			return true;
		}

		return false;
	}

	private void validateInputTextView( final View v ) {
		final String str = String.valueOf( ( (TextView) v ).getText() );
		if ( TextUtils.isEmpty( str ) ) {
			// Restore to the old value as we don't allow empty values
			this.updateInputTextView();
		} else {
			// Check the new value and ensure it's in range
			final int current = this.getSelectedPos( str.toString() );
			this.setValueInternal( current, true );
		}
	}
}
