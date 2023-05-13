package org.herac.tuxguitar.app.view.component.table;

import org.herac.tuxguitar.app.TuxGuitar;
import org.herac.tuxguitar.graphics.control.TGLayout;
import org.herac.tuxguitar.graphics.control.TGMeasureImpl;
import org.herac.tuxguitar.song.models.TGTrack;
import org.herac.tuxguitar.ui.resource.UIColor;
import org.herac.tuxguitar.ui.resource.UIColorModel;
import org.herac.tuxguitar.ui.resource.UIPainter;
import org.herac.tuxguitar.util.TGBeatRange;

public class TGTableCanvasPainter {
	
	private static final UIColorModel COLOR_BLACK = new UIColorModel(0x00, 0x00, 0x00);
	
	private TGTableViewer viewer;
	private TGTrack track;
	private static final int SELECTION_ALPHA = 128;
	
	public TGTableCanvasPainter(TGTableViewer viewer,TGTrack track){
		this.viewer = viewer;
		this.track = track;
	}
	
	protected void paintTrack(TGTableRow row, UIPainter painter){
		int x = -this.viewer.getHScrollSelection();
		int y = 0;
		float size = this.viewer.getTable().getRowHeight();
		float width = row.getPainter().getBounds().getWidth();
		boolean playing = TuxGuitar.getInstance().getPlayer().isRunning();
		
		UIColor colorBlack = this.viewer.getUIFactory().createColor(COLOR_BLACK);
		UIColor colorBackground = this.viewer.getColorModel().createBackground(this.viewer.getContext(), 3);
		
		painter.setLineWidth(UIPainter.THINNEST_LINE_WIDTH);
		painter.setBackground(colorBackground);
		painter.initPath(UIPainter.PATH_FILL);
		painter.setAntialias(false);
		painter.addRectangle(0, y, width, size);
		painter.closePath();
		
		UIColor trackColor = this.viewer.getUIFactory().createColor(this.track.getColor().getR(), this.track.getColor().getG(), this.track.getColor().getB());
		painter.setBackground(trackColor);
		painter.setForeground(trackColor);

		TGLayout layout = viewer.getEditor().getTablature().getViewLayout();
		TGBeatRange beatRange = viewer.getEditor().getTablature().getSelector().getBeatRange();

		
		int count = this.track.countMeasures();
		for(int j = 0;j < count;j++){
			TGMeasureImpl measure = (TGMeasureImpl) this.track.getMeasure(j);
			if(isRestMeasure(measure)){
				painter.initPath(UIPainter.PATH_DRAW);
				painter.setAntialias(false);
				painter.addRectangle(x, y, size - 2, size - 1);
				painter.closePath();
			}else{
				painter.initPath(UIPainter.PATH_FILL);
				painter.setAntialias(false);
				painter.addRectangle(x,y,size - 1,size );
				painter.closePath();
			}
			boolean hasCaret = TuxGuitar.getInstance().getTablatureEditor().getTablature().getCaret().getMeasure().equals(measure);
			if((playing && measure.isPlaying(this.viewer.getEditor().getTablature().getViewLayout())) || (!playing && hasCaret)){
				painter.setBackground(colorBlack);
				painter.initPath(UIPainter.PATH_FILL);
				painter.setAntialias(false);
				painter.addRectangle(x + 4, y + 4, size - 9, size - 8);
				painter.closePath();
				painter.setBackground(trackColor);
			}
			
			if (beatRange.containsMeasure(measure)) {
				painter.setBackground(layout.getResources().getSelectionColor());
				painter.setAlpha(SELECTION_ALPHA);
				painter.initPath(UIPainter.PATH_FILL);
				painter.addRectangle(x + 1.5f, y + 1.5f, size - 3f, size - 3f);
				painter.closePath();
/*
				painter.setAlpha(SELECTION_BORDER_ALPHA);
				painter.setForeground(getCaretColor(factory, trackColor, colorBackground, isRestMeasure));
				painter.initPath(UIPainter.PATH_DRAW);
				painter.addRectangle(x + 1.5f, y + 1.5f, size - 3f, size - 3f);
				painter.closePath();
*/
				painter.setAlpha(255);
				painter.setBackground(trackColor);
			}
			
			x += size;
		}
		
		trackColor.dispose();
		colorBackground.dispose();
		colorBlack.dispose();
	}
	
	private boolean isRestMeasure(TGMeasureImpl measure){
		int beatCount = measure.countBeats();
		for(int i = 0; i < beatCount; i++){
			if( !measure.getBeat(i).isRestBeat() ){
				return false;
			}
		}
		return true;
	}
}
