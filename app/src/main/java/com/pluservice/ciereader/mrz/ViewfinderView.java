/*
 * Copyright (C) 2008 ZXing authors
 * Copyright 2011 Robert Theis
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
package com.pluservice.ciereader.mrz;

import java.util.List;

import com.pluservice.ciereader.R;
import com.pluservice.ciereader.camera.CameraManager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.view.View;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the result text.
 * <p>
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
public final class ViewfinderView extends View {
  //private static final long ANIMATION_DELAY = 80L;
  
  /**
   * Flag to draw boxes representing the results from TessBaseAPI::GetRegions().
   */
  static final boolean DRAW_REGION_BOXES = false;
  
  /**
   * Flag to draw boxes representing the results from TessBaseAPI::GetTextlines().
   */
  static final boolean DRAW_TEXTLINE_BOXES = true;
  
  /**
   * Flag to draw boxes representing the results from TessBaseAPI::GetStrips().
   */
  static final boolean DRAW_STRIP_BOXES = false;
  
  /**
   * Flag to draw boxes representing the results from TessBaseAPI::GetWords().
   */
  static final boolean DRAW_WORD_BOXES = true;
  
  /**
   * Flag to draw word text with a background varying from transparent to opaque.
   */
  static final boolean DRAW_TRANSPARENT_WORD_BACKGROUNDS = false;
  
  /**
   * Flag to draw boxes representing the results from TessBaseAPI::GetCharacters().
   */
  static final boolean DRAW_CHARACTER_BOXES = false;
  
  /**
   * Flag to draw the text of words within their respective boxes from TessBaseAPI::GetWords().
   */
  static final boolean DRAW_WORD_TEXT = false;
  
  /**
   * Flag to draw each character in its respective box from TessBaseAPI::GetCharacters().
   */
  static final boolean DRAW_CHARACTER_TEXT = false;
  
  private CameraManager cameraManager;
  private final Paint paint;
  private final int maskColor;
  private final int frameColor;
  private final int cornerColor;
  private OcrResultText resultText;
  private String[] words;
  private List<Rect> regionBoundingBoxes;
  private List<Rect> textlineBoundingBoxes;
  private List<Rect> stripBoundingBoxes;
  private List<Rect> wordBoundingBoxes;
  private List<Rect> characterBoundingBoxes;
  //  Rect bounds;
  private Rect previewFrame;
  private Rect rect;
  
  // This constructor is used when the class is built from an XML resource.
  public ViewfinderView(Context context, AttributeSet attrs) {
    super(context, attrs);
    
    // Initialize these once for performance rather than calling them every time in onDraw().
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Resources resources = getResources();
    maskColor = resources.getColor(R.color.viewfinder_mask);
    frameColor = resources.getColor(R.color.viewfinder_frame);
    cornerColor = resources.getColor(R.color.viewfinder_corners);
    
    previewFrame = new Rect();
    rect = new Rect();
  }
  
  public void setCameraManager(CameraManager cameraManager) {
    this.cameraManager = cameraManager;
  }
  
  @SuppressWarnings("unused")
  @Override
  public void onDraw(Canvas canvas) {
    
    Rect mainFrame = cameraManager.getFramingRect();
    if (mainFrame == null) {
      return;
    }
    
    int width = canvas.getWidth();
    int height = canvas.getHeight();
    
    // Draw the exterior (i.e. outside the framing rect) darkened
    paint.setColor(maskColor);
    canvas.drawRect(0, 0, width, mainFrame.top, paint);
    canvas.drawRect(0, mainFrame.top, mainFrame.left, mainFrame.bottom + 1, paint);
    canvas.drawRect(mainFrame.right + 1, mainFrame.top, width, mainFrame.bottom + 1, paint);
    canvas.drawRect(0, mainFrame.bottom + 1, width, height, paint);
    
    Rect secondaryFrame = cameraManager.getSecondaryFramingRect();
    //canvas.drawRect(0, 0, width, secondaryFrame.top, paint);
    //canvas.drawRect(0, secondaryFrame.top, secondaryFrame.left, secondaryFrame.bottom + 1, paint);
    //canvas.drawRect(secondaryFrame.right + 1, secondaryFrame.top, width, secondaryFrame.bottom + 1, paint);
    //canvas.drawRect(0, secondaryFrame.bottom + 1, width, height, paint);
    
    // If we have an OCR result, overlay its information on the viewfinder.
    /*if (resultText != null) {
  
      // Only draw text/bounding boxes on viewfinder if it hasn't been resized since the OCR was requested.
      Point bitmapSize = resultText.getBitmapDimensions();
      previewFrame = cameraManager.getFramingRectInPreview();
      if (bitmapSize.x == previewFrame.width() && bitmapSize.y == previewFrame.height()) {
        
        float scaleX = mainFrame.width() / (float) previewFrame.width();
        float scaleY = mainFrame.height() / (float) previewFrame.height();
        
        if (DRAW_REGION_BOXES) {
          regionBoundingBoxes = resultText.getRegionBoundingBoxes();
          for (int i = 0; i < regionBoundingBoxes.size(); i++) {
            paint.setAlpha(0xA0);
            paint.setColor(Color.MAGENTA);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(1);
            rect = regionBoundingBoxes.get(i);
            canvas.drawRect(mainFrame.left + rect.left * scaleX,
                    mainFrame.top + rect.top * scaleY,
                    mainFrame.left + rect.right * scaleX,
                    mainFrame.top + rect.bottom * scaleY, paint);
          }
        }
        
        if (DRAW_TEXTLINE_BOXES) {
          // Draw each textline
          textlineBoundingBoxes = resultText.getTextlineBoundingBoxes();
          paint.setAlpha(0xA0);
          paint.setColor(Color.RED);
          paint.setStyle(Style.STROKE);
          paint.setStrokeWidth(1);
          for (int i = 0; i < textlineBoundingBoxes.size(); i++) {
            rect = textlineBoundingBoxes.get(i);
            canvas.drawRect(mainFrame.left + rect.left * scaleX,
                    mainFrame.top + rect.top * scaleY,
                    mainFrame.left + rect.right * scaleX,
                    mainFrame.top + rect.bottom * scaleY, paint);
          }
        }
        
        if (DRAW_STRIP_BOXES) {
          stripBoundingBoxes = resultText.getStripBoundingBoxes();
          paint.setAlpha(0xFF);
          paint.setColor(Color.YELLOW);
          paint.setStyle(Style.STROKE);
          paint.setStrokeWidth(1);
          for (int i = 0; i < stripBoundingBoxes.size(); i++) {
            rect = stripBoundingBoxes.get(i);
            canvas.drawRect(mainFrame.left + rect.left * scaleX,
                    mainFrame.top + rect.top * scaleY,
                    mainFrame.left + rect.right * scaleX,
                    mainFrame.top + rect.bottom * scaleY, paint);
          }
        }
        
        if (DRAW_WORD_BOXES || DRAW_WORD_TEXT) {
          // Split the text into words
          wordBoundingBoxes = resultText.getWordBoundingBoxes();
          //      for (String w : words) {
          //        Log.e("ViewfinderView", "word: " + w);
          //      }
          //Log.d("ViewfinderView", "There are " + words.length + " words in the string array.");
          //Log.d("ViewfinderView", "There are " + wordBoundingBoxes.size() + " words with bounding boxes.");
        }
        
        if (DRAW_WORD_BOXES) {
          paint.setAlpha(0xFF);
          paint.setColor(0xFF00CCFF);
          paint.setStyle(Style.STROKE);
          paint.setStrokeWidth(1);
          for (int i = 0; i < wordBoundingBoxes.size(); i++) {
            // Draw a bounding box around the word
            rect = wordBoundingBoxes.get(i);
            canvas.drawRect(
                    mainFrame.left + rect.left * scaleX,
                    mainFrame.top + rect.top * scaleY,
                    mainFrame.left + rect.right * scaleX,
                    mainFrame.top + rect.bottom * scaleY, paint);
          }
        }
        
        if (DRAW_WORD_TEXT) {
          words = resultText.getText().replace("\n", " ").split(" ");
          int[] wordConfidences = resultText.getWordConfidences();
          for (int i = 0; i < wordBoundingBoxes.size(); i++) {
            boolean isWordBlank = true;
            try {
              if (!words[i].equals("")) {
                isWordBlank = false;
              }
            } catch (ArrayIndexOutOfBoundsException e) {
              e.printStackTrace();
            }
            
            // Only draw if word has characters
            if (!isWordBlank) {
              // Draw a white background around each word
              rect = wordBoundingBoxes.get(i);
              paint.setColor(Color.WHITE);
              paint.setStyle(Style.FILL);
              if (DRAW_TRANSPARENT_WORD_BACKGROUNDS) {
                // Higher confidence = more opaque, less transparent background
                paint.setAlpha(wordConfidences[i] * 255 / 100);
              } else {
                paint.setAlpha(255);
              }
              canvas.drawRect(mainFrame.left + rect.left * scaleX,
                      mainFrame.top + rect.top * scaleY,
                      mainFrame.left + rect.right * scaleX,
                      mainFrame.top + rect.bottom * scaleY, paint);
              
              // Draw the word in black text
              paint.setColor(Color.BLACK);
              paint.setAlpha(0xFF);
              paint.setAntiAlias(true);
              paint.setTextAlign(Align.LEFT);
              
              // Adjust text size to fill rect
              paint.setTextSize(100);
              paint.setTextScaleX(1.0f);
              // ask the paint for the bounding rect if it were to draw this text
              Rect bounds = new Rect();
              paint.getTextBounds(words[i], 0, words[i].length(), bounds);
              // get the height that would have been produced
              int h = bounds.bottom - bounds.top;
              // figure out what textSize setting would create that height of text
              float size = (((float) (rect.height()) / h) * 100f);
              // and set it into the paint
              paint.setTextSize(size);
              // Now set the scale.
              // do calculation with scale of 1.0 (no scale)
              paint.setTextScaleX(1.0f);
              // ask the paint for the bounding rect if it were to draw this text.
              paint.getTextBounds(words[i], 0, words[i].length(), bounds);
              // determine the width
              int w = bounds.right - bounds.left;
              // calculate the baseline to use so that the entire text is visible including the descenders
              int text_h = bounds.bottom - bounds.top;
              int baseline = bounds.bottom + ((rect.height() - text_h) / 2);
              // determine how much to scale the width to fit the view
              float xscale = ((float) (rect.width())) / w;
              // set the scale for the text paint
              paint.setTextScaleX(xscale);
              canvas.drawText(words[i], mainFrame.left + rect.left * scaleX, mainFrame.top + rect.bottom * scaleY - baseline, paint);
            }
            
          }
        }
      }
    }*/
    
    // Draw a two pixel solid border inside the framing rect
    paint.setAlpha(0);
    paint.setStyle(Style.FILL);
    paint.setColor(frameColor);
    
    canvas.drawRect(mainFrame.left, mainFrame.top, mainFrame.right + 1, mainFrame.top + 2, paint);
    canvas.drawRect(mainFrame.left, mainFrame.top + 2, mainFrame.left + 2, mainFrame.bottom - 1, paint);
    canvas.drawRect(mainFrame.right - 1, mainFrame.top, mainFrame.right + 1, mainFrame.bottom - 1, paint);
    canvas.drawRect(mainFrame.left, mainFrame.bottom - 1, mainFrame.right + 1, mainFrame.bottom + 1, paint);
  
    canvas.drawRect(secondaryFrame.left, secondaryFrame.top, secondaryFrame.right + 1, secondaryFrame.top + 2, paint);
    canvas.drawRect(secondaryFrame.left, secondaryFrame.top + 2, secondaryFrame.left + 2, secondaryFrame.bottom - 1, paint);
    canvas.drawRect(secondaryFrame.right - 1, secondaryFrame.top, secondaryFrame.right + 1, secondaryFrame.bottom - 1, paint);
    canvas.drawRect(secondaryFrame.left, secondaryFrame.bottom - 1, secondaryFrame.right + 1, secondaryFrame.bottom + 1, paint);
    
    // Draw the framing rect corner UI elements
    /*paint.setColor(cornerColor);
    canvas.drawRect(mainFrame.left - 15, mainFrame.top - 15, mainFrame.left + 15, mainFrame.top, paint);
    canvas.drawRect(mainFrame.left - 15, mainFrame.top, mainFrame.left, mainFrame.top + 15, paint);
    canvas.drawRect(mainFrame.right - 15, mainFrame.top - 15, mainFrame.right + 15, mainFrame.top, paint);
    canvas.drawRect(mainFrame.right, mainFrame.top - 15, mainFrame.right + 15, mainFrame.top + 15, paint);
    canvas.drawRect(mainFrame.left - 15, mainFrame.bottom, mainFrame.left + 15, mainFrame.bottom + 15, paint);
    canvas.drawRect(mainFrame.left - 15, mainFrame.bottom - 15, mainFrame.left, mainFrame.bottom, paint);
    canvas.drawRect(mainFrame.right - 15, mainFrame.bottom, mainFrame.right + 15, mainFrame.bottom + 15, paint);
    canvas.drawRect(mainFrame.right, mainFrame.bottom - 15, mainFrame.right + 15, mainFrame.bottom + 15, paint);*/
  }
  
  public void drawViewfinder() {
    invalidate();
  }
  
  /**
   * Adds the given OCR results for drawing to the view.
   *
   * @param text Object containing OCR-derived text and corresponding data.
   */
  public void addResultText(OcrResultText text) {
    resultText = text;
  }
  
  /**
   * Nullifies OCR text to remove it at the next onDraw() drawing.
   */
  public void removeResultText() {
    resultText = null;
  }
}
