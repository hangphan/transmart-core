<%--
  User: riza
  Date: 23-04-13
  Time: 11:33
--%>

%{--Input Container--}%
<div id="gtContainer"></div>

%{--Plot wrapper--}%
<div id="gtPlotWrapper"></div>

%{-- template --}%
<extjs-tpl id="template-group-test-plot" class="x-hidden">
	<div id="plotResultContainer" class="plotResultContainer">

		<div id="plotBody" class="plotBody">
			<div id="plotCurve">
				<g:img dir="images/tempImages/guest-GroupTest-100000" file="GroupTestPlot.png" height='50%' width='50%'></g:img>
			</div>
		</div>

		<div id="gtDownload" class="downloadBtnInnerPage"></div>

	</div>
</extjs-tpl>